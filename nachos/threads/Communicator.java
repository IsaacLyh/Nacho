package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;
import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	private int word;
	private int num_speaker = 0;
	private int num_listener = 0;
	private boolean msg_ready;
	private Lock genLock;
	Condition2 speak_cond;
	Condition2 listen_cond;
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		//initialize msg status to false
		//initialize condition variables for speak and listen
		//initialize lock for all operations;
		msg_ready = false;
        genLock = new Lock();
        speak_cond  = new Condition2(genLock);
        listen_cond = new Condition2(genLock);
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		genLock.acquire(); //lock critical section -->//num_speaker;
        num_speaker++;
        //while loop to check if there's any listener
        while (msg_ready || num_listener == 0) {
            speak_cond.sleep();	//if msg is ready but there's no listener-- let listener sleep;
        }              

        //main action of speak(Only active when there are listeners)
        this.word = word;  //set current speking word to word
        msg_ready = true;  //ready the message
        listen_cond.wakeAll(); //wake all waiting threads  
        num_speaker--;            //msg should be heard by 1 listener so number of speaker should be reduced by 1;

        genLock.release(); //unlock critical section for additional access
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		//lock critical sections which is num_listeners
		genLock.acquire();   
		num_listener++;
        //check if msg is ready or not
        //if not ready, wake up speakers to speak
        //put listeners to sleep since msg isn't ready
        while(!msg_ready) {   
            speak_cond.wakeAll();    
            listen_cond.sleep();    
        }                     
        //main action of listen
        int word = this.word; 
        //after listening to the words
        //decase number of speaker by 1 and set msg_ready to false because no long ready
        msg_ready = false;  
        num_listener--;     
        genLock.release(); 
        //return what the listener heard
        return word; 
	}

	//class for listener
	//Private Communicator comn;
	//Private int msg
	private static class Listener implements Runnable{
		
		private Communicator comn;
		private int msg;
		
		//constructor-- set internal communicator
		public Listener(Communicator com){
			comn = com;
		}

		//run listen method
		public void run(){
			msg = comn.listen();
		}

	}

	//class for speakers
	//Private Communicator comn;
	//Int msg to be sent;
	private static class Speaker implements Runnable{
		private Communicator comn;
		private int msg_send;
		
		//constructor that takes in communicator specified and msg intended;
		public Speaker(Communicator com, int msg){
			msg_send = msg;
			comn = com;
		}
		//call speak method to send msg
		public void run(){
			comn.speak(this.msg_send);
		}
	}

	// test here
	public static void selfTest(){
		System.out.println("<------------------------------->");
		System.out.println("in Communicator.selfTest()");
		
		Random rn = new Random();
		Communicator com = new Communicator();
		int numListener = 1 + rn.nextInt(10), numSpeaker = 1 + rn.nextInt(10);
		System.out.println("Currently " + numListener + " Listeners, " + numSpeaker + " Speakers.");
		
		ArrayList<KThread> t = new ArrayList();
		KThread tmp;
		for(int i=0; i<numListener; i++){
			tmp = new KThread(new Listener(com));
			tmp.setName("Listener" + i);
			t.add(tmp);
		}
		System.out.println("finish making listeners");
		KThread.yield();
		
		for(int i=0; i<numSpeaker; i++){
			tmp = new KThread(new Speaker(com, i));
			tmp.setName("Speaker" + i);
			t.add(tmp);
		}
		System.out.println("finish making speakers");
		KThread.yield();
		Collections.shuffle(t);
		
		for(int i=0; i<numListener+numSpeaker; i++){
			System.out.println("call .fork() for " + t.get(i).getName());
			t.get(i).fork();
		}	
		/*			
		for(int i=0; i<numListener+numSpeaker; i++) {
			t.get(i).join();
			System.out.println("finish join() for " + t.get(i).getName());
		}
		*/
		System.out.println("finish Communicator.test()");
	}
}

