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
