/**
 * 
 */
package collate;

import java.util.*;

/**
 * @author tunder
 *
 */
public class VolumeSummary {
	Map<String, Integer> wordsPerGenre;
	String cleanID;
	String volGenre;
	
	public VolumeSummary (String cleanID, String volGenre, Map<String, Integer> wordsPerGenre) {
		this.cleanID = cleanID;
		this.volGenre = volGenre;
		this.wordsPerGenre = wordsPerGenre;
	}
}
