package cartographer;

import java.io.File;
import java.io.IOException;

public class Cartographer {

	public static void main(String[] args) throws IOException {
		new Generate()
			.setInputJar(new File("1.13-pre6-merged.jar"))
			.setMcVersion("1.13-pre6")
			.setOutputMappings(new File("output.tiny"))
			.setHistoryFile(new File("history.txt"))
			.start();
	}

}
