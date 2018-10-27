package cartographer;

import com.google.common.base.Stopwatch;

import java.io.File;
import java.io.IOException;

public class Main {

	public static void main(String[] args) throws IOException {
		Stopwatch timer = Stopwatch.createStarted();
		generate("18w43b");
		update("18w43b", "18w43c");
		System.out.println("Completed in " + timer.stop());
	}

	private static void generate(String version) throws IOException {
		System.out.println("Generating new mappings for " + version);

		MinecraftLibProvider minecraftLibProvider = new MinecraftLibProvider(version);
		new Cartographer()
			.setNewJar(minecraftLibProvider.minecraftJar())
			.setOutputMappingsFile(new File("mappings/" + version + ".mappings"))
			.setOutputMappingsTinyFile(new File("mappings/" + version + ".tiny"))
			.setNewConstructorFile(new File("mappings/" + version + ".constructors"))
			.setHistoryFile(new File("mappings/history.txt"))
			.setLibraryProvider(minecraftLibProvider)
			.setLogFile(new File("logs/generation_" + version + ".txt"))
			.setOutputJar(new File("finaljars/mapped." + version + ".jar"))
			//.setSourcesDir(new File("sources/" + version))
			.resetHistory()
			.start();
	}

	private static void update(String source, String target) throws IOException {
		System.out.println("Updating mappings from " + source + " to " + target);
		MinecraftLibProvider sourceProvider = new MinecraftLibProvider(source);
		MinecraftLibProvider targetProvider = new MinecraftLibProvider(target);
		new Cartographer()
			.setOldJar(sourceProvider.minecraftJar())
			.setNewJar(targetProvider.minecraftJar())
			.setLibraryProvider(targetProvider)
			.setOldMappingsFile(new File("mappings/" + source + ".mappings"))
			.setOutputMappingsFile(new File("mappings/" + target + ".mappings"))
			.setOutputMappingsTinyFile(new File("mappings/" + target + ".tiny"))
			.setNewConstructorFile(new File("mappings/" + target + ".constructors"))
			.setOldConstructorFile(new File("mappings/" + source + ".constructors"))
			.setMatchesFile(new File("matches/" + source + "-" + target + ".match"))
			.setHistoryFile(new File("mappings/history.txt"))
			.setLogFile(new File("logs/update_" + source + "_" + target + ".txt"))
			.setOutputJar(new File("finaljars/mapped." + target + ".jar"))
			//.setSourcesDir(new File("sources/" + target))
			.start();
	}

}
