## AbstractQueuedSynchronizer 同步器源码分析

## 排它锁
### 入口方法--```acquire```

```java
public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }
```
`tryAcquire(int)`方法是需要用户实现的自定义hook，也就是说，如果用户自己判断当前线程可以获取到执行权（往往是时序上第一个线程），则继续在当前线程上执行代码，否则：

1. 调用 `addWaiter(Node.EXCLUSIVE)`,将自己放在队列尾部
2. 将代表本线程的Node，传入到`acquireQueued`方法中

下面是`acquireQueued`的代码：

```java
  final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
```
在代码中，p是node的前置节点，也就是说：

1.当当前节点node为头结点的后置节点，并且hook为true时，返回false，意味着当前线程获取到了执行权。

2.如果1的条件不满足，则判断```shouldParkAfterFailedAcquire```,看名字可知，这是一个关于是否该把当前线程park的判断，代码如下：
```java
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL)
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */
            return true;
        if (ws > 0) {
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            /*
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }
```
该方法做了以下几件事：

1. 判断前置节点的```waitStatus```是否为```Node.SIGNAL```,如果是，则代表当前线程可被park。
2. 如果不是，查看是否大于0，大于0的状态只有cancel，这里把node前面所有被取消的线程从队列中移除
3. 将前置节点的状态原子性的改为```Node.SIGNAL```

**这里需要注意的是，在方法`acquireQueued`中是用无限for来调用的上述方法，也就是说，如果一个线程未获取到执行权，最多循环三次就会使上述方法返回true，从而使代码进入到`parkAndCheckInterrupt`中**

`parkAndCheckInterrupt`中的代码很简单--park住当前线程，并且在返回线程的中断状态。如下：
```java
private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }
```
在该方法返回至`acquire`之后，其实是根据`parkAndCheckInterrupt`的结果恢复线程的中断标志。

分析到这里我们就知道了所谓加锁的过程就是--**第一个通过用户hook的线程会获取到执行权，其他线程会因为不符合同步器自身条件或者用户hook而进入被park状态--对第一个线程来说，整个执行过程是无锁的，单线程的，这降低了系统了开销**

## 唤醒机制 -- `release`
`release`方法的代码很简洁：
```java
public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        return false;
    }
```
`tryRelease`同样是用户的自定义hook，也就是说必须先通过用户的hook才能进行同步器的唤醒过程，唤醒过程`unparkSuccessor`如下：
```java
private void unparkSuccessor(Node node) {
        /*
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         */
        int ws = node.waitStatus;
        if (ws < 0)
            compareAndSetWaitStatus(node, ws, 0);

        /*
         * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         */
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        if (s != null)
            LockSupport.unpark(s.thread);
    }
```
唤醒过程简单来说就是找到头结点head 的下一个节点绑定的线程，并直接将其unpark。但是细看代码还是有很多有意思的地方：

1. 将当前节点的状态置0时，是允许失败的
2. 循环是从tail向head步进

对1来说，注释明确说明了失败或者被等待线程改变node的状态都是ok的，这只是一个清除动作。

对2来说，从tail向head 步进 可以避免 队列中出现为null或者被取消状态的node导致队列从中间断开而导致的后加入的node无法被唤醒，举个例子：如果因为某种情况导致下面队列产生：

> [1]->[2]x->[null]x->[4]->[5]

如果从head开始遍历，只能遍历到2，而新加入队列的node在5之后，导致大量线程阻塞。
而从tail开始，虽然也只能遍历到4，但是可以释放后加入的线程。


```java
public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }
```

## 共享锁篇
### 入口方法 `acquireShared`
```java
public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }
``` 
`tryAcquireShared` 是 用户自定义的hook 方法，当返回值大于0时，当前线程获得执行权限，当同时多个共享线程启动时，时间上 后启动的线程不会加入队列&阻塞，而是并发执行，以读写为例，多个读线程并不会阻塞，当写线程获取到执行权限的时候才会将之后的读或者写操作阻塞住。下面假设下面线程的执行顺序来讨论执行逻辑：
> [r1]->[r2]->[w1]->[w2]->[r3]->[r4]
首先读是可共享操作，所以r1和r2执行时是并发执行的，此时w1来尝试获取执行权限，进入到`doAcquireShared`方法。
```java
 private void doAcquireShared(int arg) {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
```
`doAcquireShared` 第一步就构造了node加入到队列当中，随后是一个循环过程--不断尝试获取执行权，如果失败就被park，里面有两个判断条件 p==head 和 r >=0,在本例子中，此时（假设）r1、2正在执行，所以r必然小于0（逻辑上），从而进入park状态。w2因为相同的原因呢进入park状态。

此时，r1结束之后，会调用`releaseShared` 方法，代码如下：
```java
public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }
```
重点是`doReleaseShared`
```java
private void doReleaseShared() {

        for (;;) {
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    unparkSuccessor(h);
                }
                else if (ws == 0 &&
                         !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            if (h == head)                   // loop if head changed
                break;
        }
    }
```
该方法主要是unpark队列中的下一个节点的线程，此时队列头是w1，将其unpark之后，w1 会再循环一次从而获取到执行权限，w2继续unpark，r3，r4也会进入unpark状态。下面是获取执行权限的代码：
```java
 if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
```
此时，w1 同时满足 p==head 和 r>=0 两个条件，获取到执行权限。
当w1执行完，相同的逻辑会执行w2，当执行完w2 将要唤醒r3的时候，会有一个线程的判断：如果是共享类型的线程，则触发一次`doReleaseShared`，代码如下：
``` java
 private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; // Record old head for check below
        setHead(node);
        /*
         * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus either before
         *     or after setHead) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }
```
当触发```doReleaseShared()```的时候，根据上面的分析，会unpark下一个节点，从而unpark r4 ,如果以后均是共享型的线程，也会依次unpark.

> 排它锁和共享锁的大致逻辑是一致的，只不过对阻塞线程的唤起策略不同：排它锁只唤醒下一个节点，共享锁则唤起下N个连续的共享节点或者一个排他节点--在代码上的体现就是在设置当前头节点的过程中，共享节点多做一次唤醒操作（当然是有条件的）。

## 重入锁--ReentrantLock
> 重入锁：假设A线程执行某块代码c，持有锁b，因为某种原因（递归）需要重新进入到c中，不需要再次竞争锁b，直接可以进入，这种锁机制称为重入锁，在非重入锁的情况下遇到上述场景，会导致自身死锁（A一直等A释放锁）。
`ReentrantLock`是JDK自身提供的重入锁实现，核心代码如下：
```java
 final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }
```
这是`ReentrantLock`非公平锁的实现，对于重入锁机制，体现在：
```java
else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
```
这一段中，在这个判断里面判断了当前线程是不是当前获得执行权的线程，如果是，更新状态，继续执行。
对于公平锁，代码实现十分相似，只是加了一个判断：
```java
 protected final boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (!hasQueuedPredecessors() &&
                    compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }
```
多了一个条件`!hasQueuedPredecessors()`,这个条件可以简单的理解为是判断当前等待队列是否有等待节点（实际判断依赖了同步器的实现），如果有，返回false 从而使当前线程加入到队列尾。
另外补充一下公平锁和非公平锁：
> 公平锁 是指 先到先得，因为同一个时刻竞争锁的不止有队列里的下一个节点，还有此刻新启动的线程，以及当前线程（比如代码loop），所以必须确保队列里的节点优先。

> 非公平锁 是指 谁得是谁，无论是哪个线程，先成功设置状态的线程获取执行权。
*二者没有谁更好，只有谁更合适。公平锁执行分布更均匀，但是上下文切换比较频繁。非公平锁会导致某些线程一直得不到执行权的问题，但是上下文切换较少，tps更高*
## 读写锁--ReentrantReadWriteLock
读写锁是共享读，非完全排它写，写时可（重入）读的一种锁，在此提到他是为了引入下一个概念--锁降级。

先假设这样一个情况：N个线程对同一数据进行循环读，当读到的数据满足一定条件C，则会对数据进行写操作P1并根据修改完的内容做一些其他操作P2

首先，在读锁未释放的时候不能开写锁，所以很直觉的一种处理方法是：读锁->解读锁->写锁->P1->解写锁->P2

但是注意解写锁这个操作，这个操作之后很可能是另一个写操作执行，这就导致P2处理的数据 实际上是过期的（数据可见性问题）

锁降级 就是为了处理这种情况，把一个写锁降级成为读锁，执行过程如下： 读锁->解读锁->写锁->P1->读锁->解写锁->P2->解读锁。

这样在解写锁之后，不可能是一个写操作，一定会是读操作，一直到最后解读锁才有可能让新的写操作插入进来，从而避免了数据不一致的问题。

另外： 为什么P2要在解写锁之后？ 因为P2可能是一个耗时的操作，放在写锁里会导致大量读写被阻塞，而放在读锁里，只会阻塞写锁（也必须阻塞写锁）

## 通知唤醒机制--Condition
Condition只需要关心`await`和`signal`两个方法即可：
### await
``` java
public final void await() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }
```
主要代码做了以下几件事：
1. 构造一个新的Condition类型的节点加入到condition的等待队列中
2. 释放锁
3. 检查当前节点是否在同步队列中，如果不在，说明当前仍在等待队列，将当前线程park，如果在，说明被唤醒，（因为唤醒会将节点加入到同步队列）
4. acquireQueued使当前线程重新竞争执行权
### signal
```java
final boolean transferForSignal(Node node) {
        /*
         * If cannot change waitStatus, the node has been cancelled.
         */
        if (!node.compareAndSetWaitStatus(Node.CONDITION, 0))
            return false;

        /*
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         */
        Node p = enq(node);
        int ws = p.waitStatus;
        if (ws > 0 || !p.compareAndSetWaitStatus(ws, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }
```
这是signal关键逻辑的代码，首先尝试将node的状态改为0（node的初始状态为0），成功后将节点加入的同步队列（`enq`),并将状态设置为SIGNAL，如果设置失败，就将当前线程unpark，此时线程会回到`acquireQueued`方法重新尝试获取执行权，获取不到就将自己重新park。

> 通过对Condition的了解，对同步器的整体有一个更完整的把握：同步器包含一个同步队列和N个等待队列，每个等待队列对应一个condition，所谓等待机制就是将当前Node（可能没有或不是）移动到对应的condition队列中，唤醒就是将condition的队列移动一个或者全部到等待队列中。
