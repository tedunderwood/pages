package pages;
import java.io.File;
import java.util.ArrayList;

public class DirectoryList {
	 
	 public static ArrayList<String> getCSVs(String path) {
	 
	  String files;
	  File folder = new File(path);
	  File[] listOfFiles = folder.listFiles(); 
	  ArrayList<String> textFiles = new ArrayList<String>();
	 
	  for (int i = 0; i < listOfFiles.length; i++) 
	  {
	 
	   if (listOfFiles[i].isFile()) 
	   {
	   files = listOfFiles[i].getName();
	       if (files.endsWith(".csv") || files.endsWith(".CSV"))
	       {
	          textFiles.add(files);
	        }
	     }
	  }
	  return textFiles;
	  
	}
}

