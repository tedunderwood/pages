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
import java.util.HashMap;
import java.util.List;

import pages.ArgumentParser;
import pages.InputFileException;
import pages.LineReader;
import pages.WarningLogger;
import handy.Table;

/**
 * @author tunder
 *
 */
public class Collate {
	
	int startdate = 1700;
	int enddate = 1899;

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
		
		String metadataPath = parser.getString("-metadata");
		Table metadata = new Table(metadataPath);
		
		String pairtreeRoot = parser.getString("-pairtreeroot");
		String slicePath = parser.getString("-slice");
		
		ArrayList<String> cleanIDs = getSlice(slicePath);
		
		Map<String, Integer> volumeDates = new HashMap<String, Integer>();
		for (String cleanID : cleanIDs) {
			String dirtyID = toDirtyID(cleanID);
			int date = 0;
			try {
				String dateString = metadata.getCell("date", dirtyID);
				date = Integer.parseInt(dateString);
			} catch (Exception ignore) {}
			volumeDates.put(cleanID, date);
		}
		
		final ExecutorService executor = Executors.newFixedThreadPool(12);
		
		ArrayList<Future<VolumeSummary>> summaries = new ArrayList<Future<VolumeSummary>>();
		for (String anID : cleanIDs) {
			VolumeSummarizer summarizeThisOne = new VolumeSummarizer(anID, pairtreeRoot, "dummy");
			Future<VolumeSummary> summary = executor.submit(summarizeThisOne);
			summaries.add(summary);
		}
		
		executor.shutdown();
		try {
			executor.awaitTermination(600, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			System.out.println("Helpful error message: Execution was interrupted.");
		}
		// block until all threads are completed
		
		List<VolumeSummary> allTheVols = new ArrayList<VolumeSummary>(); 
		for (Future<VolumeSummary> aFuture : summaries) {
			try{
				VolumeSummary thisResult = aFuture.get();
				allTheVols.add(thisResult);
			} catch (Exception e) {
				System.out.println("Sophisticated error handling.");
			}
		}
		
		// Let's create a json object hierarchy.
		
		for (VolumeSummary vol : allTheVols) {
			String thisID = vol.cleanID;
			Map<String, Integer> wordsPerGenre = vol.wordsPerGenre;
			String volGenre = vol.volGenre;
			
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
	
	private static String toCleanID(String dirtyID) {
		dirtyID = dirtyID.replace(":", "+");
		dirtyID = dirtyID.replace("/", "=");
		return dirtyID;
	}
	
	private static String toDirtyID(String cleanID) {
		cleanID = cleanID.replace("+", ":");
		cleanID = cleanID.replace("=", "/");
		return cleanID;
	}

}
