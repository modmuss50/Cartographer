package cartographer;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

//Based off https://github.com/sfPlayer1/Matcher/blob/master/src/matcher/serdes/MatchesIo.java
public class Matches {

	BiMap<String, String> classMatches = HashBiMap.create();
	BiMap<String, String> methodMatches = HashBiMap.create();
	BiMap<String, String> fieldMatches = HashBiMap.create();

	public Matches read(File file) throws IOException {
		BufferedReader reader = Files.newBufferedReader(file.toPath());
		String line;
		Pair<String, String> currentClass = null;

		while ((line = reader.readLine()) != null) {
			if (line.isEmpty()){
				continue;
			}
			if (line.startsWith("c\t")) {
				int pos = line.indexOf('\t', 2);
				if (pos == -1 || pos == 2 || pos + 1 == line.length()){
					throw new IOException("invalid matches file");
				}
				String oldName = line.substring(2, pos).replaceAll(";", "").substring(1); //Removes some stuff we dont need
				String newName = line.substring(pos + 1).replaceAll(";", "").substring(1);
				currentClass = Pair.of(oldName, newName);
				classMatches.put(oldName, newName);

			} else if (line.startsWith("\tm\t") || line.startsWith("\tf\t")) {
				if (currentClass != null) {
					int pos = line.indexOf('\t', 3);
					if (pos == -1 || pos == 3 || pos + 1 == line.length()){
						throw new IOException("invalid matches file");
					}
					String oldName = line.substring(3, pos);
					String newName = line.substring(pos + 1);
					if (line.charAt(1) == 'm') {
						methodMatches.put(currentClass.getLeft() + "." + oldName, currentClass.getRight() + "." + newName);
					} else {
						fieldMatches.put(currentClass.getLeft() + "." + oldName, currentClass.getRight() + "." + newName);
					}
				}
			}
		}
		return this;
	}

}
