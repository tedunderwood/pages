/**
 * 
 */
package collate;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Map;

import pages.ArgumentParser;
import pages.InputFileException;
import pages.LineReader;
import pages.WarningLogger;

/**
 * @author tunder
 *
 */
public class Collate {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		String logfile = "/Users/tunder/output/warninglog.txt";
		ArgumentParser parser = new ArgumentParser(args);
		if (parser.isPresent("-log")) {
			logfile = parser.getString("-log");
		}
		
		WarningLogger.initializeLogger(true, logfile);
		
		String pairtreeRoot = parser.getString("-pairtreeroot");
		String slicePath = parser.getString("-slice");
		
		ArrayList<String> dirtyHtids = getSlice(slicePath);
		
		final ExecutorService executor = Executors.newFixedThreadPool(12);
		
		ArrayList<Future<Map<String, Integer>>> summaries = new ArrayList<Future<Map<String, Integer>>>();
		for (String anID : dirtyHtids) {
			VolumeSummarizer summarizeThisOne = new VolumeSummarizer(anID, pairtreeRoot, "dummy");
			Future<Map<String, Integer>> summary = executor.submit(summarizeThisOne);
			summaries.add(summary);
		}
		
		executor.shutdown();
		try {
			executor.awaitTermination(600, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			System.out.println("Helpful error message: Execution was interrupted.");
		}
		// block until all threads are completed
		
		try{
			for (Future<Map<String, Integer>> aFuture : summaries) {
				Map<String, Integer> theResult = aFuture.get();
				System.out.println(theResult.get("in"));
			}
		} catch (Exception e) {
			System.out.println("Sophisticated error handling.");
		}
	}
	
	private static ArrayList<String> getSlice(String slicePath) {
		ArrayList<String> dirtyHtids;
		LineReader getHtids = new LineReader(slicePath);
		try {
			dirtyHtids = getHtids.readList();
		}
		catch (InputFileException e) {
			System.out.println("Missing slice file: " + slicePath);
			dirtyHtids = null;
		}
		return dirtyHtids;
	}

}
