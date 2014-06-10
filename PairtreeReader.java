package pages;

import java.util.ArrayList;

public class PairtreeReader {
	String dataPath;
	static final int NUMCOLUMNS = 3;
	Pairtree pairtree;
	WarningLogger logger;
	
	public PairtreeReader(String dataPath, Pairtree pairtree) {
		this.dataPath = dataPath;
		this.pairtree = pairtree;
	}
	
	private String getPairtreePath(String dirtyID) {
		int periodIndex = dirtyID.indexOf(".");
		String prefix = dirtyID.substring(0, periodIndex);
		// the part before the period
		String pathPart = dirtyID.substring(periodIndex+1);
		// everything after the period
		String ppath = pairtree.mapToPPath(pathPart);
		String encapsulatingDirectory = pairtree.cleanId(pathPart);
		String wholePath = dataPath + prefix + "/pairtree_root/" + ppath + "/"+ encapsulatingDirectory + 
				"/" + encapsulatingDirectory + ".pg.tsv";
		return wholePath;
	}
	
	public ArrayList<String> getVolume(String dirtyID) {
		String path = getPairtreePath(dirtyID);
		System.out.println(path);
		LineReader reader = new LineReader(path);
		ArrayList<String> filelines = new ArrayList<String>();
		
		try {
			filelines = reader.readList();
		}
		catch (InputFileException e) {
			WarningLogger.addFileNotFound(path);
		}
		return filelines;
	}
	
}