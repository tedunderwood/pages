/**
 * 
 */
package pages;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author tunder
 *
 */
public class EnsembleProducer implements Runnable {
	ArrayList<String> filesToProcess;
	BlockingQueue<Unknown> outQueue;
	String inputDir;
	boolean isPairtree;
	int numModels;
	
	public EnsembleProducer (ArrayList<String> filesToProcess, BlockingQueue<Unknown> outQueue, String inputDir, 
			boolean isPairtree, int numModels) {
		this.filesToProcess = filesToProcess;
		this.inputDir = inputDir;
		this.outQueue = outQueue;
		this.isPairtree = isPairtree;
		this.numModels = numModels;
	}
	
	@Override
	public void run() {
		for (String thisFile : filesToProcess) {
			ArrayList<String> filelines;
			String cleanID = PairtreeReader.cleanID(thisFile);
			
			if (isPairtree) {
				PairtreeReader reader = new PairtreeReader(inputDir);
				filelines = reader.getVolume(thisFile);
				Unknown mystery = new Unknown(cleanID, filelines, numModels);
				try {
					outQueue.offer(mystery, 10, TimeUnit.MINUTES);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			else {
				String volumePath = inputDir + thisFile + ".pg.tsv";
				LineReader fileSource = new LineReader(volumePath);
				try {
					filelines = fileSource.readList();
					Unknown mystery = new Unknown(cleanID, filelines, numModels);
					outQueue.offer(mystery, 10, TimeUnit.MINUTES);
				}
				catch (InputFileException e) {
					WarningLogger.addFileNotFound(thisFile);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
	
			}
		}
	}
	

}
