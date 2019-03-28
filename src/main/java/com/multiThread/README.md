## AbstractQueuedSynchronizer 同步器源码分析--排它锁篇


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
