package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;
import java.util.Random;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	LinkedList<waitThread> waitQueue;
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		long current_time = Machine.timer().getTime();
		if(waitQueue == null || waitQueue.size()== 0){
			return;
		}
		else{
			int least_index = findLeast(waitQueue);
			
			while(waitQueue.size() > 0 && least_index != -1){
					waitThread cur = waitQueue.get(least_index);
					cur.thread.ready();
					waitQueue.remove(cur);
					least_index = findLeast(waitQueue);
			}
		}
	}

	public int findLeast(LinkedList<waitThread> input){
		if(input == null){
			return -1;
		}
		long least = Machine.timer().getTime();
		int index = -1;
		for(int i = 0; i < input.size(); i++){
			if(input.get(i).time < least){
				least = input.get(i).time;
				index = i;
			}
		}
		return index;	
	}
	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		Lock guard = new Lock();
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;
		boolean status = Machine.interrupt().disable();
		waitThread wait = new waitThread(KThread.currentThread(),wakeTime);
		if(waitQueue == null){
			waitQueue = new LinkedList<waitThread>();	
			waitQueue.add(wait);
			KThread.sleep();
		}
		else{
			waitQueue.add(wait);
			KThread.sleep();
		}
		Machine.interrupt().restore(status);	
	}

	private class waitThread implements Comparable{
		private long time;
		private KThread thread;
		private boolean wake = false;
		public waitThread(KThread k, long waittime){
			time = waittime;
			thread = k;
		}


		public int compareTo(Object o) {
	   	 	waitThread cut = (waitThread) o;

	    	if (time < cut.time){
            	return -1;
	   		 }
	    	else if (time > cut.time){
            	return 1;
	   		}
	    	else{
	        	return thread.compareTo(cut.thread);        
	    	}
		}	

	}
	

	private static class AlarmTest implements Runnable{
		AlarmTest(long x){
			this.time = x;
		}
		public void run(){
			System.out.println(KThread.currentThread().getName() + " alarm");
			ThreadedKernel.alarm.waitUntil(time);	
			System.out.println(KThread.currentThread().getName() + " woken up");
		}
		
		private long time;
	}

	public static void selfTest(){
		System.out.println("<----------------------------------->");
		System.out.println("in Alarm.selfTest()");
		/*
		KThread t1 = new KThread(
			new Runnable(){
				public void run(){
					Random rn = new Random();
					// for single thread
					KThread thrds1 = new KThread(new AlarmTest(100));
					thrds1.setName("Single thread").fork();
					int num1 = 1000+rn.nextInt(200);
					for(int i=0; i<num1; i++) KThread.yield();
					//thrds1.join();
				}
			}
		);
		t1.setName("AlarmTest");
		t1.fork();
		KThread.yield();
		//t1.join();

		KThread t2 = new KThread(
			new Runnable(){
				public void run(){
					Random rn = new Random();

					// for multiple thread
					KThread thrds[] = new KThread[25];
					for(int i=0; i<25; i++){
						thrds[i] = new KThread( new AlarmTest(200+i*20) );
						thrds[i].setName("Thread" + i + " in multiple thread").fork();
					}					
					int num2 = 2500+rn.nextInt(2500);
					for(int i=0; i<num2; i++) KThread.yield();
					
					//for(int i=0; i<25; i++) thrds[i].join();
				}
			}
		);
		t2.setName("AlarmTest");
		t2.fork();
		KThread.yield();
		//t2.join();
		*/
		Random rn = new Random();
		// for single thread
		KThread thrds1 = new KThread(new AlarmTest(100));
		thrds1.setName("Single thread").fork();
		int num1 = 1000+rn.nextInt(200);
		for(int i=0; i<num1; i++) KThread.yield();
		thrds1.join();
		
		// for multiple thread
		KThread thrds[] = new KThread[25];
		for(int i=0; i<25; i++){
			thrds[i] = new KThread( new AlarmTest(200+rn.nextInt(25)*20) );
			thrds[i].setName("Thread" + i + " in multiple thread").fork();
		}					
		int num2 = 2500+rn.nextInt(2500);
		for(int i=0; i<num2; i++) KThread.yield();
		for(int i=0; i<25; i++) thrds[i].join();
		
		System.out.println("Finish alarm test!");
	}
}
