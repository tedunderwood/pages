package hmm;

import java.util.ArrayList;

public class GenreList {
	public ArrayList<String> genreLabels;
	
	public GenreList() {
		genreLabels = new ArrayList<String>();
		genreLabels.add("begin");
		genreLabels.add("end");
		// Whenever you create a new GenreList, it must have entries for the "begin" and "end" genres,
		// because every Markov sequence has these as endposts.
	}
	
	public void addLabel(String newLabel) {
		if (!genreLabels.contains(newLabel)) {
			genreLabels.add(newLabel);
		}
	}
	
	public int getIndex(String genre) {
		return genreLabels.indexOf(genre);
	}
	
	public int getSize() {
		return genreLabels.size();
	}

}
