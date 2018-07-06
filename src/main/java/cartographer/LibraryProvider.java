package cartographer;

import cuchaz.enigma.analysis.ParsedJar;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

//TODO lets cache this stuff
public abstract class LibraryProvider {

	List<ParsedJar> parsedJarList = new ArrayList<>();

	abstract void getLibs(List<File> fileList);

	public void load() throws IOException {
		List<File> libs = new ArrayList<>();
		getLibs(libs);
		for (File file : libs) {
			try {
				parsedJarList.add(new ParsedJar(new JarFile(file)));
			} catch (Exception e) {
				throw new RuntimeException("Failed to read jar " + file.getName(), e);
			}
		}
	}

	public ClassNode getClassNode(String className) {
		for (ParsedJar jar : parsedJarList) {
			ClassNode classNode = jar.getClassNode(className);
			if (classNode != null) {
				return classNode;
			}
		}
		return null;
	}

}
