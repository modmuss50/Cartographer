package cartographer;

import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.analysis.ParsedJar;
import cuchaz.enigma.mapping.*;
import cuchaz.enigma.mapping.entry.ClassEntry;
import cuchaz.enigma.mapping.entry.FieldDefEntry;
import cuchaz.enigma.mapping.entry.MethodDefEntry;
import cuchaz.enigma.mapping.entry.ReferencedEntryPool;
import cuchaz.enigma.throwables.MappingConflict;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

public class Generate {

	File inputJar;

	File outputMappings;

	File historyFile;

	String mcVersion;

	String packageName = "net/minecraft";

	Mappings newMappings;

	MappingHistory mappingHistory;

	private JarIndex index;

	public void start() throws IOException {
		Validate.notNull(inputJar);
		Validate.notNull(outputMappings);
		Validate.notNull(historyFile);
		Validate.isTrue(!packageName.isEmpty());
		Validate.isTrue(!mcVersion.isEmpty());

		index = new JarIndex(new ReferencedEntryPool());
		index.indexJar(new ParsedJar(new JarFile(inputJar)), true);

		newMappings = new Mappings();

		if (mappingHistory == null) {
			mappingHistory = MappingHistory.newMappingsHistory();
		}

		for (ClassEntry classEntry : index.getObfClassEntries()) {
			handleClass(classEntry);
		}

		for (MethodDefEntry methodEntry : index.getObfBehaviorEntries()) {
			handleMethod(methodEntry);
		}

		for (FieldDefEntry fieldEntry : index.getObfFieldEntries()) {
			handleField(fieldEntry);
		}

		System.out.println("Writing history to disk");
		mappingHistory.writeToFile(historyFile);

		System.out.println("Exporting mappings");
		MappingsEnigmaWriter mappingWriter = new MappingsEnigmaWriter();
		mappingWriter.write(outputMappings, newMappings, false);
	}

	private void handleClass(ClassEntry classEntry) {
		//TODO check if it used in the old mappings
		if (!classEntry.getClassName().contains("/")) { //Skip everything already in a package
			handleNewClass(classEntry);
		} else {
			//Add the class to the mappings without a new name
			try {
				newMappings.addClassMapping(new ClassMapping(classEntry.getClassName()));
			} catch (MappingConflict mappingConflict) {
				throw new RuntimeException("Mappings failed to apply", mappingConflict);
			}
		}
		//classEntry.
	}

	private void handleNewClass(ClassEntry classEntry) {
		try {
			String newClassName = mappingHistory.generateClassName(mcVersion);
			System.out.println("NC: " + classEntry.getClassName() + " -> " + newClassName);
			newMappings.addClassMapping(new ClassMapping(classEntry.getClassName(), packageName + "/" + newClassName));
		} catch (MappingConflict mappingConflict) {
			throw new RuntimeException("Mappings failed to apply", mappingConflict);
		}
	}

	private void handleMethod(MethodDefEntry methodEntry) {
		if (!Util.isObfuscatedIdentifier(methodEntry, true, index)) {
			return;
		}
		if (methodEntry.getName().contains("lambda$")) { //Nope
			return;
		}
		if (methodEntry.isConstructor()) {
			return;
		}
		//TODO this is a horrible way to figure out if it the entry is mapped
		if (methodEntry.getOwnerClassEntry() != null
			&& methodEntry.getOwnerClassEntry().getPackageName() != null
			&& methodEntry.getOwnerClassEntry().getPackageName().contains("net/minecraft")
			&& methodEntry.getName().length() > 2) {
			return;
		}
		handleNewMethod(methodEntry);
	}

	private void handleNewMethod(MethodDefEntry methodEntry) {
		String signature = methodEntry.getDesc().toString();
		String newMethodName = mappingHistory.generateMethodName(signature, mcVersion);
		System.out.println("NM: " + methodEntry.getName() + signature + " -> " + newMethodName + signature);

		newMappings.getClassByObf(methodEntry.getOwnerClassEntry()).addMethodMapping(new MethodMapping(methodEntry.getName(), methodEntry.getDesc(), newMethodName));
	}

	private void handleField(FieldDefEntry fieldEntry) {
		if (!Util.isObfuscatedIdentifier(fieldEntry, true, index)) {
			return;
		}
		//TODO this is a horrible way to figure out if it the entry is mapped
		if (fieldEntry.getOwnerClassEntry() != null
			&& fieldEntry.getOwnerClassEntry().getPackageName() != null
			&& fieldEntry.getOwnerClassEntry().getPackageName().contains("net/minecraft")
			&& fieldEntry.getName().length() > 2) {
			return;
		}
		handleNewField(fieldEntry);
	}

	private void handleNewField(FieldDefEntry fieldEntry) {
		String signature = fieldEntry.getDesc().toString();
		String newFieldName = mappingHistory.generateFieldName(signature, mcVersion);
		System.out.println("NF: " + fieldEntry.getName() + signature + " -> " + newFieldName + signature);
		newMappings.getClassByObf(fieldEntry.getOwnerClassEntry()).addFieldMapping(new FieldMapping(fieldEntry.getName(), fieldEntry.getDesc(), newFieldName, Mappings.EntryModifier.UNCHANGED));
	}

	public Generate setInputJar(File inputJar) {
		this.inputJar = inputJar;
		return this;
	}

	public Generate setOutputMappings(File outputMappings) {
		this.outputMappings = outputMappings;
		return this;
	}

	public Generate setHistoryFile(File historyFile) {
		this.historyFile = historyFile;
		return this;
	}

	public Generate setMcVersion(String mcVersion) {
		this.mcVersion = mcVersion;
		return this;
	}

	public Generate setPackageName(String packageName) {
		this.packageName = packageName;
		return this;
	}
}
