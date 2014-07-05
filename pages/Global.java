/**
 * 
 */
package pages;

/**
 * @author tunder
 * Stylistically, this is probably not classy Java.
 * It"s just a public container for some global constants
 * that I want to be able to access from anywhere.
 * 
 */
public class Global {
	public static boolean allVsAll = false;
	
	public static final String[] STRUCTURALFEATURES = { "posInVol", "lineLengthRatio",
		"capRatio", "wordRatio", "distanceFromMid", "allCapRatio", "maxInitialRatio", "maxPairRatio", 
		"wordsPerLine", "totalWords", "typeToken", "commasNorm", "periodsNorm", "quotationsNorm",
		"exclamationsNorm", "questionsNorm", "endWithPunct", "endWithNum", "startWithName", "startWithRubric",
		"capSequence", "capSeqNorm", "startRatio", "absWordRatio", "metaBiography", "metaFiction",
		"litprob", "bioprob"};
	
		// "posInVol" = pagenum / totalpages
		// "lineLengthRatio" = textlines / mean lines per page
		// "capRatio" = caplines / textlines
		// "wordRatio" = words on page / mean words per page
		// "distanceFromMid" = abs( 0.5 - posInVol)
		// "allCapRatio" = words in all caps / words on this page
		// "maxInitalRatio" = largest number of repeated initials / textlines
		// "maxPairRatio" = largest number of repeats for alphabetically adjacent initials / textlines
		// "wordsPerLine" = total words on page / total lines on page
		// "totalWords" = total words on page
		// "typeToken" = types on page / tokens on page
		// "commasNorm" = commas normalized for wordcount
		// "periodsNorm" = periods normalized for wordcount
		// "quotationsNorm" = quotation marks normalized for wordcount
		// "exclamationsNorm" = exclamation points normalized for wordcount
		// "questionsNorm" = questions normalized for wordcount
		// "endWithPunct" = Proportion of lines ending with punctuation.
		// "endWithNum" = Proportion of lines ending with a digit as either of last two chars.
		// "startWithName" = Proportion of lines starting with a word that might be a name.
		// "startWithRubric" = Proportion of lines starting with a capitalized word that ends w/ a period.
		// "capSequence" = Largest number of capitalized initials in alphabetical sequence.
		// "capSeqNorm" = Sequential caps normalized for the number of capitalized lines.
		// "startRatio" = (startWName * startWRubric) / wordNotinVocab
		// "absWordRatio" = absolute deviation from word mean for vol, normalized by word mean
	public static final int FEATURESADDED = STRUCTURALFEATURES.length;
	
//	public static final String[][] CONVERSIONS = { { "colop", "back" },
//		{ "epigr", "front" }, { "trv", "non" }, { "ora", "non" }, {"notes", "non"},
//		{ "argum", "non" }, { "errat", "back" }, { "toc", "front" },
//		{ "title", "front" }, { "impri", "front" },
//		{ "gloss", "back" }, {"subsc", "catal"} };
	
	public static String[][] CONVERSIONS = { {"subsc", "front"}, {"argum", "non"}, {"pref", "non"},
		{"aut", "non"}, {"bio", "non"}, {"toc", "front"}, {"title", "front"}, {"bookp", "front"},
		{"bibli", "back"}, {"gloss", "back"}, {"index", "back"}, {"epi", "fic"}, {"errat", "non"}, {"notes", "non"}, {"ora", "non"}, 
		{"let", "non"}, {"trv", "non"}, {"lyr", "poe"}, {"nar", "poe"}, {"vdr", "dra"}, {"pdr", "dra"},
		{"clo", "dra"}, {"impri", "front"}, {"libra", "back"} };
	
	public static boolean undersample = false;
	
	public static void separateBiography() {
		for (String[] aPair : CONVERSIONS) {
			if (aPair[0].equals("bio")) aPair[1] = "bio";
			if (aPair[0].equals("aut")) aPair[1] = "bio";
			if (aPair[0].equals("let")) aPair[1] = "bio";
		}
	}
	
	public static void separateIndex() {
		for (String[] aPair : CONVERSIONS) {
			if (aPair[0].equals("index")) aPair[1] = "index";
			if (aPair[0].equals("gloss")) aPair[1] = "index";
			if (aPair[0].equals("bibli")) aPair[1] = "index";
		}
	}
}
