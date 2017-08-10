package nachos.vm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	public Boolean save_status = false;
	public Boolean[] reployer = new Boolean[10];
	public HashMap<Integer,Page> formated_sections = new HashMap<Integer,Page>();
	public static final int pageSize = Processor.pageSize;
	public static VMKernel kernel = null;
	
	public VMProcess(){
		super();
		kernel = (VMKernel) ThreadedKernel.kernel;	
		if(kernel == null){
			System.out.println("Kernel failure");		
		}
	}

	@Override
	public void saveState(){
		save_status = true;
		kernel.sync_TLB(true);
	}

	@Override
	public void restoreState(){
		save_status = false;
	}

	//completed
	@Override
	protected boolean loadSections() {
		boolean ret_stats = false;
		int vpn_load = -1;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection curr = coff.getSection(s);
			for (int i=0; i<curr.getLength(); i++){
				int vpn = curr.getFirstVPN() + i;
				if(vpn == -1){
					return ret_stats;
				}
				vpn_load = vpn;
				formated_sections.put(vpn, new Coff_con(curr, vpn));
			}
		}
		if(vpn_load < 0){
			System.out.println("Detected error when changing vpn....aborting");
			return ret_stats;
		}
		else{
			vpn_load++;
		}
		while(vpn_load < numPages - 1){
			ret_stats = false;
			formated_sections.put(vpn_load, new StackPage(vpn_load));
			vpn_load++;
			ret_stats = true;
		}
		return ret_stats;
	}
	//completed
	
	@Override
	protected void unloadSections(){
		kernel.get_new_pages(processID, numPages);
	}
	
	//completed
	@Override
	protected void loadArguments(int ent_off, int str_off, byte[][] argv) {
		try{
			if(numPages < 1){
				System.out.println("NumPages invalid");
				return;
			}
			else{
				int page_num = numPages -1;
				arg_Con input = new arg_Con(ent_off, str_off, argv);
				formated_sections.put(page_num, input);
			}
		}
		catch(Exception e){
			System.out.println("Bad arg..aborting" + e.toString());
			return;
		}
	}
	//completed
	

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exception</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	@Override
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss:
			handle_TLB_miss(processor.readRegister(processor.regBadVAddr));
			return;
		default:
			super.handleException(cause);
			break;
		}
	}

	protected boolean addr_verify(int vaddr) {
		int vpn = Processor.pageFromAddress(vaddr);
		if(vpn >= 0 && vpn < numPages){
			return true;
		}
		return false;
	}


	public void handle_TLB_miss(int vaddr){
			Processor p = Machine.processor();
			if(addr_verify(vaddr)){
			boolean inserting = true;
			TranslationEntry trans_entry = get_Page(Processor.pageFromAddress(vaddr));
			for(int i = 0; i < p.getTLBSize() && inserting; i++){
				TranslationEntry curr_entry = p.readTLBEntry(i);
				inserting = handle_TLB_miss_verify(i,curr_entry,trans_entry,inserting);
			}
			if(inserting){
				random_victim(trans_entry);
			}
			kernel.unPinPage(trans_entry.ppn);
		}
	}

	public void random_victim(TranslationEntry trans_entry){
		Random rand_gen = new Random();
		Processor p = Machine.processor();
		int randomIndex = rand_gen.nextInt(p.getTLBSize());
		if (p.readTLBEntry(randomIndex).dirty || p.readTLBEntry(randomIndex).used){
			kernel.sync_entry(p.readTLBEntry(randomIndex).ppn, p.readTLBEntry(randomIndex).used, p.readTLBEntry(randomIndex).dirty);	
		}
		p.writeTLBEntry(randomIndex, trans_entry);
	}

	public boolean handle_TLB_miss_verify(int pos,TranslationEntry curr_entry,TranslationEntry trans_entry,boolean inserting){
		Processor p = Machine.processor();
		if(curr_entry.ppn == trans_entry.ppn && curr_entry.ppn >= 0){
			if(inserting){
				inserting = false;
				if(save_status){
					reployer[0] = save_status;
				}
				p.writeTLBEntry(pos, trans_entry);
			} 
			else if(curr_entry.valid){
				curr_entry.valid = false;
				if(save_status){
					reployer[0] = save_status;
				}
				p.writeTLBEntry(pos, curr_entry);
			}
		}
		else if(inserting){
			if(!curr_entry.valid){
				p.writeTLBEntry(pos, trans_entry);
				if(save_status){
					reployer[0] = save_status;
				}
				inserting = false;
			}
		}
		return inserting;
	}

	public TranslationEntry get_Page(int vpn) {
		TranslationEntry ret_val = null;
		if(ret_val == null){
			save_status = !save_status;
			reployer[3] = save_status;
		}
		if (formated_sections.containsKey(vpn)){
			reployer[0] = save_status;
			ret_val = formated_sections.get(vpn).execute();
		}
		else if ((ret_val = kernel.check_n_pin(vpn, processID)) == null){
			reployer[1] = save_status;
			ret_val = kernel.handle_PageFault(vpn, processID);
		}
		if(ret_val == null){
			System.out.println("Val is null");
		}
		if(ret_val.ppn >= 16){ 
			System.out.println("too large ppn associated with vpn: " + ret_val.vpn);
		}
		return ret_val;
	}

	

	@Override
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		return writeVirtualMemory(vaddr, data, offset, length, true);
	}
	
	public int writeVirtualMemory(int vaddr, byte[] data, boolean unpin) {
		return writeVirtualMemory(vaddr, data, 0, data.length, unpin);
	}
	
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length, boolean unpin) {
		// from reference solution;
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		Boolean processCheck = Processor.pageFromAddress(vaddr) < 0;
		Boolean addr_check = Processor.pageFromAddress(vaddr) >= numPages;
		if (processCheck||addr_check){
			return -1;
		}
		int amount = 0;
		while (length > 0){
		    int vpn = Processor.pageFromAddress(vaddr);
		    int off = Processor.offsetFromAddress(vaddr);
		    int transfer = Math.min(length, pageSize-off);
		    int ppn = pinVirtualPage(vpn, true);
		    if (ppn == -1){
				break;
			}
		    System.arraycopy(data, offset, memory, ppn*pageSize + off,transfer);
		    if (unpin){
		    	unpinVirtualPage(ppn);
		    }
		    vaddr += transfer;
		    offset += transfer;
		    amount += transfer;
		    length -= transfer;	    
		}
		return amount;
	}
	
	@Override
	protected int pinVirtualPage(int vpn, boolean writable) {
		if(vpn < 0 || vpn >= numPages){
			return -1;
		}
		TranslationEntry entry = get_Page(vpn);
		if(!entry.valid || entry.vpn != vpn){
		    return -1;
		}
		if(writable){
		    if (entry.readOnly){
				return -1;
		    }
		    entry.dirty = true;
		}
		entry.used = true;
		return entry.ppn;
	}
	
	@Override	
	protected void unpinVirtualPage(int ppn) {
		kernel.unPinPage(ppn);
	}

	public abstract class Page{
		public abstract TranslationEntry execute();
	}

	public class Coff_con extends Page{
		public CoffSection coffSection;
		public int vpn;

		public Coff_con(CoffSection coff, int _vpn) {
			this.coffSection = coff;
			this.vpn = _vpn;
		}

		@Override
		public TranslationEntry execute(){
			int sectionNumber = vpn - coffSection.getFirstVPN();
			Boolean check =	formated_sections.remove(vpn) != null; 
			reployer[0] = check;
			if(check){
				reployer[0] = reployer[1];
			}
			Lib.assertTrue(check);
			TranslationEntry ret_val = kernel.K_free(vpn, processID);
			coffSection.loadPage(sectionNumber, ret_val.ppn);
			ret_val.readOnly = coffSection.isReadOnly();
			return ret_val;
		}

	}

	public class StackPage extends Page{

		public StackPage(int _vpn){
			vpn = _vpn;
		}

		@Override
		public TranslationEntry execute(){
			Lib.assertTrue(formated_sections.remove(vpn) != null);
			TranslationEntry ret_val = kernel.K_free(vpn, processID);
			ret_val.readOnly = false;
			return ret_val;
		}
		public int vpn;
	}
	
	public class arg_Con extends Page{
		public byte[][] argv;
		public int str_off;
		public int ent_off;
		public arg_Con(int _ent_off, int _str_off, byte[][] _argv) {
			ent_off = _ent_off; 
			str_off = _str_off; 
			argv = _argv;
		}

		@Override
		public TranslationEntry execute() {
			boolean check = formated_sections.remove(numPages - 1) != null;
			Lib.assertTrue(check);
			TranslationEntry ret_val = kernel.K_free(numPages - 1, processID);
			for (int i = 0; i < argv.length; i++) {
				byte[] str_offBytes = Lib.bytesFromInt(str_off);
				Lib.assertTrue(writeVirtualMemory(ent_off,str_offBytes,false) == 4);
				ent_off += 4;
				Lib.assertTrue(writeVirtualMemory(str_off, argv[i],false) == argv[i].length);
				str_off += argv[i].length;
				Lib.assertTrue(writeVirtualMemory(str_off, new byte[]{0},false) == 1);
				str_off += 1;
			}
			ret_val.readOnly = true;
			return ret_val;
		}

	}
	
}