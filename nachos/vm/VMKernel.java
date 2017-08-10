package nachos.vm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
		for (int i = 0; i < memory_bank.length; i++){
			memory_bank[i] = new mem_Node(i);
		}
	}

	@Override
	public void initialize(String[] args) {
		super.initialize(args);
		mem_Lock = new Lock();
		pinned = new Condition(mem_Lock);
		swap = new Swap();
	}

	@Override
	public void run() {
		super.run();
	}

	@Override
	public void terminate() {
		swap.close();		
		super.terminate();
	}

	private mem_Node clockAlgorithm(){
		mem_Lock.acquire();
		while(num_pinned == memory_bank.length){ 
			pinned.sleep();
		}
		sync_TLB(false);
		clock_part1();
		mem_Node curr_node = memory_bank[victim];
		num_pinned++;
		curr_node.pinned = true;
		int ppn = victim;
		for(int i = 0; i < Machine.processor().getTLBSize(); i++){
			TranslationEntry clock_entry = Machine.processor().readTLBEntry(i);
			if(clock_entry.valid && clock_entry.ppn == ppn){
				clock_entry.valid = false;
				Machine.processor().writeTLBEntry(i, clock_entry);
				break;
			}
		}
		mem_Node curr_node2 = null;
		if(curr_node.processID > -1){
			curr_node2 = invertedPageTable.remove(new t_key(curr_node.translationEntry.vpn, curr_node.processID));
		}
		mem_Lock.release();
		if(curr_node2 != null){ 
			swap.mode_selector(0,curr_node,0,0,0,0);
		}
		return curr_node;
	}

	private void clock_part1(){
		while(true){
			victim = (victim+1)%(memory_bank.length);
			mem_Node page = memory_bank[victim];
			if (page.pinned){
				continue;
			}
			if(reployer_2[0] != null){
				reployer_2[1] = page.pinned;
			}
			else{
				reployer_2[0] = page.pinned;
			}
			if (page.processID == -1){
				break;
			}
			if(page.translationEntry.valid == false){
				break;
			}
			if (page.translationEntry.used) {
				page.translationEntry.used = false;
			}
			else {
				break;
			}
		}
	}

	public TranslationEntry K_free(int vpn, int pid) {
		mem_Node page = clockAlgorithm();
		int pageBeginAddress = page.translationEntry.ppn * Processor.pageSize;
		Arrays.fill(Machine.processor().getMemory(), pageBeginAddress, pageBeginAddress + Processor.pageSize, (byte) 0);
		config(page,pid,vpn);
		mem_Lock.acquire();
		invertedPageTable.put(new t_key(vpn, pid), page);
		mem_Lock.release();
		return page.translationEntry;
	}

	public void config(mem_Node page,int pid, int vpn){
		page.translationEntry.vpn = vpn;
		page.translationEntry.valid = true;
		page.translationEntry.used = false;
		page.translationEntry.dirty = false;
		page.translationEntry.readOnly = false;
		page.processID = pid;
	}

	public TranslationEntry handle_PageFault(int vpn, int pid) {
		if (!swap.mode_selector(2,null,vpn,pid,0,0)){
			return null;
		}
		TranslationEntry curr = K_free(vpn, pid);
		swap.mode_selector(1,null,vpn,pid,curr.ppn,0);
		return curr;
	}

	public void	get_new_pages(int pid, int maxVPN){
		mem_Lock.acquire();
		for(int i = 0; i < memory_bank.length; i++){
			mem_Node curr_node_page = memory_bank[i];
			reployer_2[0] = false;
			if(curr_node_page.processID == pid){
				t_key current_result = new t_key(curr_node_page.translationEntry.vpn,curr_node_page.processID);
				invertedPageTable.remove(current_result);
				reployer_2[2] = false;
				curr_node_page.processID = -1;
				reployer_2[1] = curr_node_page.translationEntry.valid;
				curr_node_page.translationEntry.valid = false;
			}
		}
		mem_Lock.release();
		swap.mode_selector(3,null,0,pid,0,maxVPN);
	}

	public void unPinPage(int ppn){
		mem_Lock.acquire();
		reployer_2[0] = false;
		mem_Node node_pin = memory_bank[ppn];
		if (node_pin.pinned){
			num_pinned--;
			reployer_2[1] = true;
		}
		node_pin.pinned = false;
		reployer_2[2] = node_pin.pinned;
		pinned.wake();
		mem_Lock.release();
	}

	public TranslationEntry check_n_pin(int vpn, int pid){
		mem_Node node_in = null;
		mem_Lock.acquire();
		t_key check_id = new t_key(vpn,pid);
		reployer_2[1] = false;
		node_in = invertedPageTable.get(check_id);
		if(node_in != null){
			if(!node_in.pinned){
				reployer_2[0] = node_in.pinned;
				num_pinned++;
			}
			reployer_2[1] = node_in.pinned;
			node_in.pinned = true;
		}
		mem_Lock.release();
		if(node_in == null){
			reployer_2[4] = false;
			return null;
		}
		else{
			reployer_2[4] = true;
			return node_in.translationEntry;
		}
	}
	public void sync_TLB(boolean action){
		Boolean done_check = true;
		for(int i = 0; i < Machine.processor().getTLBSize(); i++){
			TranslationEntry curr = Machine.processor().readTLBEntry(i);
			if(curr.valid){
				TranslationEntry entry_curr = memory_bank[curr.ppn].translationEntry;
				if(entry_curr.valid && entry_curr.vpn == curr.vpn){
					reployer_2[0] = entry_curr.used;
					entry_curr.used |= curr.used;
					reployer_2[1] = entry_curr.used;
					entry_curr.dirty |= curr.dirty;
					reployer_2[2] = entry_curr.dirty;
				}
			}
			if(action&&done_check){
				curr.valid = false;
				reployer_2[3] = curr.valid;
				Machine.processor().writeTLBEntry(i, curr);
			}
		}
	}
	
	public void sync_entry(int ppn, boolean used, boolean dirty){
		mem_Lock.acquire();
		reployer_2[0] = false;
		TranslationEntry curr = memory_bank[ppn].translationEntry;
		curr.dirty |= dirty;
		reployer_2[1] = curr.dirty;
		curr.used |= used;
		reployer_2[2] = curr.used;
		mem_Lock.release();
	}

	private Boolean[] reployer_2 = new Boolean[10];

	private static final char dbgVM = 'v';

	private mem_Node[] memory_bank = new mem_Node[Machine.processor().getNumPhysPages()];

	private int victim = 0;

	private Hashtable<t_key,mem_Node> invertedPageTable = new Hashtable<t_key,mem_Node>();

	private Lock mem_Lock;

	private Swap swap;

	private int num_pinned;

	private Condition pinned;

	private static class t_key{
		private Integer vpn; 
		private Integer pid;

		public t_key(int _vpn, int _pid){
			vpn = _vpn;
			pid = _pid;
		}

		@Override
		public int hashCode(){
			return Processor.makeAddress(vpn, pid );
		}

		@Override
		public boolean equals(Object x){
			if(this == x){
				return true;
			}
			else if(x instanceof t_key){
				t_key x_result = (t_key)x;
				Boolean ret = vpn.equals(x_result.vpn)&&pid.equals(x_result.pid);
				return ret;
			} 
			else{
				return false;
			}
		}
	}

	private static class mem_Node{
		TranslationEntry translationEntry;
		int processID = -1;
		boolean pinned = false;
		public mem_Node (int ppn){
			translationEntry = new TranslationEntry(this.processID, ppn, false, false, false, false);
		}
	}

	private class Swap{
		private OpenFile target_file;
		private HashMap<t_key, entry_swap> table_swap = new HashMap<t_key, entry_swap>();
		private LinkedList<entry_swap> swap_list = new LinkedList<entry_swap>();
		private Lock lock_target_file = new Lock();
		private int num_ent=0;

		public Swap(){
			target_file = fileSystem.open("fswap", true);;
		}

		public Boolean mode_selector(int mode,mem_Node input_node,int vpn,int pid,int ppn,int maxVPN){
			switch(mode){
				case 0:
					reployer_2[0] = true;
					if(input_node.translationEntry.valid){
						entry_swap entry_swap = null;
						reployer_2[0] = false;
						t_key tk = new t_key(input_node.translationEntry.vpn, input_node.processID);
						lock_target_file.acquire();
						if(input_node.translationEntry.dirty||!table_swap.containsKey(tk)){
							reployer_2[1] = input_node.translationEntry.dirty;
							if(swap_list.size() > 0){
								reployer_2[2] = false;
								entry_swap = swap_list.removeFirst();
								entry_swap.readOnly = input_node.translationEntry.readOnly;
								reployer_2[3] = entry_swap.readOnly;
							}
							else{
								reployer_2[4] = true;
								entry_swap = new entry_swap(num_ent++, input_node.translationEntry.readOnly); 
							}
							table_swap.put(tk, entry_swap);
						}
						lock_target_file.release();
						if(entry_swap != null){
							if(reployer_2[0]){
								reployer_2[4] = false;
							}
							int result_size = target_file.write(entry_swap.pn * Processor.pageSize,Machine.processor().getMemory(),input_node.translationEntry.ppn * Processor.pageSize,Processor.pageSize);
							Lib.assertTrue(result_size == Processor.pageSize);
						}
					}
					return false;
				case 1:
					reployer_2[0] = false;
					lock_target_file.acquire();
					entry_swap entry_swap = table_swap.get(new t_key(vpn, pid));
					reployer_2[1] = true;
					lock_target_file.release();
					if(entry_swap != null){
						int result_size2 = target_file.read(entry_swap.pn * Processor.pageSize,Machine.processor().getMemory(),ppn * Processor.pageSize,Processor.pageSize);
						Lib.assertTrue(result_size2 == Processor.pageSize);
						reployer_2[2] = (result_size2 == Processor.pageSize);
						memory_bank[ppn].translationEntry.readOnly = entry_swap.readOnly;
						if(reployer_2[0]){
							reployer_2[3] = memory_bank[ppn].translationEntry.readOnly;
						}
					}
					return false;
				case 2:
					reployer_2[0] = true;
					lock_target_file.acquire();
					boolean val_ret = table_swap.containsKey(new t_key(vpn,pid));
					if(reployer_2[0]){
						reployer_2[1] = val_ret;
					}
					lock_target_file.release();
					return val_ret;
				case 3:
					reployer_2[0] = true;
					lock_target_file.acquire();
					entry_swap targeted_entry;
					for(int i = 0; i < maxVPN; i++){
						if((targeted_entry = table_swap.get(new t_key(i,pid))) != null){
							if(reployer_2[0]){
								reployer_2[1] = reployer_2[0];
								reployer_2[2] = false;
							}
							swap_list.add(targeted_entry);
						}
					}
					lock_target_file.release();
				default:
					return false;
			}

		}

		public void close(){
			target_file.close();
			fileSystem.remove(target_file.getName());
		}

		private class entry_swap{
			public int pn;
			public boolean readOnly;
			public entry_swap(int num, boolean input){
				pn = num;
				readOnly = input;
			}
		}	
	}
}