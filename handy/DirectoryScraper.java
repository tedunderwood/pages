package handy;
import java.io.File;
import java.util.ArrayList;

public class DirectoryScraper {
	 
	/**
	 * This method searches a directory for files that end with ".pg.tsv,"
	 * and returns the portion of the filename preceding that extension,
	 * which will be equal to the 'clean' HathiTrust volume ID.
	 * 
	 * @param path The directory to be searched.
	 * @return An ArrayList of 'clean' HathiTrust volume IDs.
	 */
	public static ArrayList<String> filesSansExtension(String path, String extension) {
		  
		  String filename;
		  int extlen = extension.length();
		  File folder = new File(path);
		  File[] listOfFiles = folder.listFiles(); 
		  
		  ArrayList<String> idParts = new ArrayList<String>();
		 
		  for (int i = 0; i < listOfFiles.length; i++) {
		 
		   if (listOfFiles[i].isFile()) {
			   filename = listOfFiles[i].getName();
		       if (filename.endsWith(extension))	{
		    	   int namelength = filename.length();
		    	   int lastNameChar = namelength - extlen;
		    	   if (lastNameChar < 1) continue;
		    	   String idPart = filename.substring(0, lastNameChar);
		           idParts.add(idPart);
		        }
		     }
		  }
		  return idParts;
		  
		}
}

