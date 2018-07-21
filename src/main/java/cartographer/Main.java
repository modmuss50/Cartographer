package cartographer;

import java.io.File;
import java.io.IOException;

public class Main {

	public static void main(String[] args) throws IOException {

		//Tests regenerating a new set of mappings for a new jar
//		new Cartographer()
//			.setNewJar(new File("1.13-pre6-merged.jar"))
//			.setOutputMappingsFile(new File("1.13-pre6.mappings"))
//			.setHistoryFile(new File("history.txt"))
//			.setOutputJar(new File("1.13-pre6-mapped.jar"))
//			.setLibraryProvider(new MinecraftLibProvider("1.13-pre6"))
//			.resetHistory()
//			.start();

		//Tests updating mappings between 2 versions without writing to disk
		//		new Cartographer()
		//			.setOldJar(new File("1.13-pre3-merged.jar"))
		//			.setNewJar(new File("1.13-pre6-merged.jar"))
		//			.setOldMappingsFile(new File("1.13-pre3.mappings"))
		//			.setOutputMappingsFile(new File("1.13-pre6.mappings"))
		//			.setMatchesFile(new File("1.13-pre3-1.13-pre6.matches"))
		//			.setHistoryFile(new File("history.txt"))
		//			.simulate()
		//			.start();

		generate("1.13-pre5");
//		update("1.13-pre5", "1.13-pre6");
//		update("1.13-pre6", "1.13-pre7");
//		update("1.13-pre7", "1.13-pre8");
//		update("1.13-pre8", "1.13-pre9");
//		update("1.13-pre9", "1.13-pre10");
//		update("1.13-pre10", "1.13");

	}

	private static void generate(String version) throws IOException {
		System.out.println("Generating new mappings for " + version);
		new Cartographer()
			.setNewJar(new File("jars/" + version +"-merged.jar"))
			.setOutputMappingsFile(new File("mappings/" + version +".mappings"))
			.setHistoryFile(new File("mappings/history.txt"))
			.setLibraryProvider(new MinecraftLibProvider(version))
			.setLogFile(new File("logs/generation_" + version + ".txt"))
			.resetHistory()
			.start();
	}

	private static void update(String source, String target) throws IOException {
		System.out.println("Updating mappings from " + source + " to " + target);
		new Cartographer()
			.setOldJar(new File("jars/" + source +"-merged.jar"))
			.setNewJar(new File("jars/" + target +"-merged.jar"))
			.setLibraryProvider(new MinecraftLibProvider(target))
			.setOldMappingsFile(new File("mappings/" + source +".mappings"))
			.setOutputMappingsFile(new File("mappings/" + target +".mappings"))
			.setMatchesFile(new File("matches/" + source + "-" + target + ".match"))
			.setHistoryFile(new File("mappings/history.txt"))
			.setLogFile(new File("logs/update_" + source + "_" + target + ".txt"))
			.start();
	}

}
