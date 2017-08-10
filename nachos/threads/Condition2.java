package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;

		waitQueue = new LinkedList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean status = Machine.interrupt().disable();
		conditionLock.release();
		waitQueue.add(KThread.currentThread());
		KThread.sleep();
		Machine.interrupt().restore(status);
		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean status = Machine.interrupt().disable();
		//wake one thread only
		if(waitQueue!=null && waitQueue.size()>0){
			waitQueue.pop().ready();
		}
		Machine.interrupt().restore(status);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean status = Machine.interrupt().disable();
		//wake everyone until waitQueue is empty
		while(waitQueue!=null && waitQueue.size()>0){
			waitQueue.pop().ready();
		}
		Machine.interrupt().restore(status);
	}
	
	/**
	 * testing function here
	 */
	private static class Condition2Test implements Runnable {
    	private Lock lock; 
   	 	private Condition2 condition; 
		Condition2Test(Lock lock, Condition2 condition) {
	    	this.condition = condition;
        	this.lock = lock;
		}
	
		public void run() {
        	lock.acquire();
       		condition.sleep();
       	 	lock.release();
		}
    }

	public static void selfTest(){
		System.out.println("<-------------------------------->");
		System.out.println("in Condition2.selfTest()");
		Lock lock = new Lock();
    	Condition2 condition = new Condition2(lock); 

    	KThread t[] = new KThread[5];
		for (int i=0; i<5; i++) {
        	t[i] = new KThread(new Condition2Test(lock, condition));
         	t[i].setName("Thread" + i).fork();
		}	
		KThread.yield();
		lock.acquire();
		System.out.println("Pinging current queue size = " + waitQueue.size());
		System.out.println("Waking up One thread");
		
		condition.wake();
		System.out.println("Pinging current queue size = " + waitQueue.size());
		condition.wakeAll();
		System.out.println("Waking up All thread");
		System.out.println("Pinging current queue size = " + waitQueue.size());
		System.out.println("Condition2 Test Finished");
		lock.release();
		System.out.println("Finish test");
	}

	private Lock conditionLock;
	private static LinkedList<KThread> waitQueue;
}
