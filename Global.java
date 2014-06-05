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
	public static final String[] STRUCTURALFEATURES = { "posInVol", "lineLengthRatio",
		"capRatio", "wordRatio", "distanceFromMid", "allCapRatio", "maxInitialRatio", 
		"maxPairRatio", "wordsPerLine", "totalWords", "typeToken" };
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
	public static final int FEATURESADDED = STRUCTURALFEATURES.length;
	
//	public static final String[][] CONVERSIONS = { { "colop", "back" },
//		{ "epigr", "front" }, { "trv", "non" }, { "ora", "non" }, {"notes", "non"},
//		{ "argum", "non" }, { "errat", "back" }, { "toc", "front" },
//		{ "title", "front" }, { "impri", "front" },
//		{ "gloss", "back" }, {"subsc", "catal"} };
	
	public static final String[][] CONVERSIONS = { {"subsc", "front"}, {"argum", "non"}, {"pref", "non"},
		{"aut", "bio"}, {"bio", "bio"}, {"toc", "front"}, {"title", "front"}, {"bookp", "front"},
		{"bibli", "back"}, {"gloss", "back"}, {"index", "back"}, {"epi", "fic"}, {"errat", "non"}, {"notes", "non"}, {"ora", "non"}, 
		{"let", "non"}, {"trv", "non"}, {"lyr", "poe"}, {"nar", "poe"}, {"vdr", "dra"}, {"pdr", "dra"},
		{"clo", "dra"}, {"impri", "front"}, {"libra", "back"} };
}
