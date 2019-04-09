## ConcurrentLinkedDeque关键源码解析
ConcurrentLinkedDeque是一个线程安全的队列，其最大的特点是lock-free，整个代码的实现仅依靠CAS操作就实现了线程安全的特性，下面对其关键代码进行解析：
#### 增加元素--linkLast
先上代码：
```java
final Node<E> newNode = newNode(Objects.requireNonNull(e));

        restartFromTail:
        for (;;)
            for (Node<E> t = tail, p = t, q;;) {
                if ((q = p.next) != null &&
                    (q = (p = q).next) != null)
                    // Check for tail updates every other hop.
                    // If p == q, we are sure to follow tail instead.
                    p = (t != (t = tail)) ? t : q;
                else if (p.prev == p) // NEXT_TERMINATOR
                    continue restartFromTail;
                else {
                    // p is last node
                    PREV.set(newNode, p); // CAS piggyback
                    if (NEXT.compareAndSet(p, null, newNode)) {
                        // Successful CAS is the linearization point
                        // for e to become an element of this deque,
                        // and for newNode to become "live".
                        if (p != t) // hop two nodes at a time; failure is OK
                            TAIL.weakCompareAndSet(this, t, newNode);
                        return;
                    }
                    // Lost CAS race to another thread; re-read next
                }
            }
```
代码主体是一个for循环加三个if分支，事实上真正起作用的是第三个else里面的cas操作，第一个分支实际上是个优化寻找的过程，因为当在队列尾部添加节点时，需要考虑：
1. 如何原子性的添加到尾部？cas操作保证了这一点
2. 如何找到尾部？多线程情况下，尾部的节点随时可能有变化。

对于2来说，如果熟悉cas操作，实际上不是问题--只要我不断的循环cas，总能得到结果，但是这样会对性能有很大的浪费，所以对于2，解决方法是添加了一个假tail节点。

为什么说是假tail呢？ 因为这个节点并不保证真的一直指向真正的尾节点，只保证在tail之前的肯定不是尾节点（废话），所以可以找寻尾节点的时候可以从tail开始找，这样一来缩小了范围，二来避免了频繁的cas。
#### 出队列
code-frist
``` kotlin
public E pollFirst() {
        for (Node<E> p = first(); p != null; p = succ(p)) {
            final E item;
            if ((item = p.item) != null
                && ITEM.compareAndSet(p, item, null)) {
                unlink(p);
                return item;
            }
        }
        return null;
    }
```
这里其实只做了一件事：把当前的头结点的item设置为null，真正的处理逻辑在unlink方法，如下：
```java
void unlink(Node<E> x) {
 

        final Node<E> prev = x.prev;
        final Node<E> next = x.next;
        if (prev == null) {
            unlinkFirst(x, next);
        } else if (next == null) {
            unlinkLast(x, prev);
        } else {
   
            Node<E> activePred, activeSucc;
            boolean isFirst, isLast;
            int hops = 1;

            // Find active predecessor
            for (Node<E> p = prev; ; ++hops) {
                if (p.item != null) {
                    activePred = p;
                    isFirst = false;
                    break;
                }
                Node<E> q = p.prev;
                if (q == null) {
                    if (p.next == p)
                        return;
                    activePred = p;
                    isFirst = true;
                    break;
                }
                else if (p == q)
                    return;
                else
                    p = q;
            }

            // Find active successor
            for (Node<E> p = next; ; ++hops) {
                if (p.item != null) {
                    activeSucc = p;
                    isLast = false;
                    break;
                }
                Node<E> q = p.next;
                if (q == null) {
                    if (p.prev == p)
                        return;
                    activeSucc = p;
                    isLast = true;
                    break;
                }
                else if (p == q)
                    return;
                else
                    p = q;
            }

            // TODO: better HOP heuristics
            if (hops < HOPS
                // always squeeze out interior deleted nodes
                && (isFirst | isLast))
                return;

            // Squeeze out deleted nodes between activePred and
            // activeSucc, including x.
            skipDeletedSuccessors(activePred);
            skipDeletedPredecessors(activeSucc);

            // Try to gc-unlink, if possible
            if ((isFirst | isLast) &&

                // Recheck expected state of predecessor and successor
                (activePred.next == activeSucc) &&
                (activeSucc.prev == activePred) &&
                (isFirst ? activePred.prev == null : activePred.item != null) &&
                (isLast  ? activeSucc.next == null : activeSucc.item != null)) {

                updateHead(); // Ensure x is not reachable from head
                updateTail(); // Ensure x is not reachable from tail

                // Finally, actually gc-unlink
                PREV.setRelease(x, isFirst ? prevTerminator() : x);
                NEXT.setRelease(x, isLast  ? nextTerminator() : x);
            }
        }
    }
```
unlink的作用不止移除元素&解绑这么简单，因为在多线程&无锁的情况下，只关心自己的node，会出问题，事实上，unlink是对队列进行一次梳理，它以本node为中心，分别找所有的前置空节点和后置空节点，相当于将这些节点全部清空（包括node本身）。
当node为头节点或者尾节点时，将会替换成两个特殊值：prevTerminator和nextTerminator。
这两个特殊值的意义在于告知其他线程头、为节点发生了变化，需要重新遍历。
###总结
1. 该队列是个双端队列，head 和tail只是辅助，并不代表真正的头尾
2. 仅用cas操作就实现了线程安全
3. 多线程编程的基本策略：防御式的编程，只处理符合条件的情况&一个方法里面通常要处理多种情况&抽取多线程下的通用逻辑
