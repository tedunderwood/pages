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
import java.util.Collection;

import pages.ArgumentParser;
import pages.InputFileException;
import pages.LineReader;
import pages.WarningLogger;

import handy.*;

import json.*;

/**
 * @author tunder
 *
 */
public class Collate {
	
	static int startdate = 1700;
	static int enddate = 1900;
	// startdate inclusive, enddate exclusive
	static String[] volgenres = {"drama", "fiction", "nonfiction", "poetry", "mixed"};
	static String[] wordgenres = {"drama", "fiction", "nonfiction", "poetry", "front", "back", "ads", "biography"};
	static String[] equivalents = {"dra", "fic", "non", "poe", "front", "back", "ads", "bio"};

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
		
		String outputPath = parser.getString("-output");
		
		String pairtreeRoot = parser.getString("-pairtreeroot");
		String slicePath = parser.getString("-slice");
		
		ArrayList<String> cleanIDs = DirectoryScraper.filesSansExtension(slicePath, ".predict");
		
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
			VolumeSummarizer summarizeThisOne = new VolumeSummarizer(anID, pairtreeRoot, slicePath);
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
		
		// Let's create some json arrays.
		
		int timespan = enddate - startdate;
		int[] zeroes = new int[timespan];
		int[] dates = new int[timespan];
		for (int i = 0; i < timespan; ++i) {
			zeroes[i] = 0;
			dates[i] = startdate + i;
		}
		
		JSONObject tlvol = new JSONObject();
		JSONObject tlword = new JSONObject();
		JSONArray years = new JSONArray(dates);
		tlvol.put("years", years);
		tlword.put("years", years);
		
		tlvol.put("genres", volgenres);
		tlword.put("genres", wordgenres);
		for (String aGenre : volgenres) {
			JSONArray aZeroArray = new JSONArray(zeroes); 
			tlvol.put(aGenre, aZeroArray);
		}
		for (String aGenre: wordgenres) {
			JSONArray aZeroArray = new JSONArray(zeroes);
			tlword.put(aGenre, aZeroArray);
		}
		
		for (VolumeSummary vol : allTheVols) {
			String thisID = vol.cleanID;
			int date = volumeDates.get(thisID);
			if (date < 1700 | date > 1899) continue;
			
			int offset = date - startdate;
			if (offset >= timespan) continue;
			
			String volGenre = vol.volGenre;
			JSONArray volseries = tlvol.getJSONArray(volGenre);
			int currentvalue = volseries.getInt(offset);
			volseries.put(offset, currentvalue +1);
			
			Map<String, Integer> wordsPerGenre = vol.wordsPerGenre;
			for (String genre : wordsPerGenre.keySet()) {
				int numwords = wordsPerGenre.get(genre);
				String translated = translateGenre(genre);
				JSONArray wordseries = tlword.getJSONArray(translated);
				currentvalue = wordseries.getInt(offset);
				wordseries.put(offset, currentvalue + numwords);
			}	
		}
		
		JSONObject verytop = new JSONObject();
		verytop.put("top_level_volume_graph", tlvol);
		verytop.put("top_level_word_graph", tlword);
		String jsonout = verytop.toString();
	    LineWriter output = new LineWriter(outputPath, false);
		output.print(jsonout);
		
	}
	
	private static String translateGenre(String shortGenre) {
		int idx = 0;
		for (int i = 0; i < equivalents.length; ++i) {
			if (shortGenre.equals(equivalents[i])) idx = i;
		}
		
		return wordgenres[idx];
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
