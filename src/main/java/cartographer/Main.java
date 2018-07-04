package cartographer;

import java.io.File;
import java.io.IOException;

public class Main {

	public static void main(String[] args) throws IOException {

		//Tests regenerating a new set of mappings for a new jar
//		new Cartographer()
//			.setNewJar(new File("1.13-pre3-merged.jar"))
//			.setNewMinecraftVersion("1.13-pre3")
//			.setOutputMappingsFile(new File("1.13-pre3.mappings"))
//			.setHistoryFile(new File("history.txt"))
//			.start();

		//Tests updating mappings between 2 versions without writing to disk
		new Cartographer()
			.setOldJar(new File("1.13-pre3-merged.jar"))
			.setNewJar(new File("1.13-pre6-merged.jar"))
			.setNewMinecraftVersion("1.13-pre6")
			.setOldMappingsFile(new File("1.13-pre3.mappings"))
			.setOutputMappingsFile(new File("1.13-pre6.mappings"))
			.setMatchesFile(new File("1.13-pre3-1.13-pre6.matches"))
			.setHistoryFile(new File("history.txt"))
			.simulate()
			.start();

	}

}
