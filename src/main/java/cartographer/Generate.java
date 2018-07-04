package cartographer;

import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.analysis.ParsedJar;
import cuchaz.enigma.mapping.*;
import cuchaz.enigma.mapping.entry.ClassEntry;
import cuchaz.enigma.mapping.entry.FieldDefEntry;
import cuchaz.enigma.mapping.entry.MethodDefEntry;
import cuchaz.enigma.mapping.entry.ReferencedEntryPool;
import cuchaz.enigma.throwables.MappingConflict;
import cuchaz.enigma.throwables.MappingParseException;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

public class Generate {

	File oldJar;
	File newJar;

	private Mappings oldMappings;
	private Mappings newMappings;
	private Matches matches;

	File oldMappingsFile;
	File outputMappingsFile;
	File historyFile;
	File matchesFile;

	String newMinecraftVersion;

	String packageName = "net/minecraft";

	private MappingHistory mappingHistory;
	private JarIndex index;

	private boolean simulate = false;

	public void start() throws IOException {
		Validate.notNull(newJar);
		Validate.notNull(outputMappingsFile);
		Validate.notNull(historyFile);
		Validate.isTrue(!packageName.isEmpty());
		Validate.isTrue(!newMinecraftVersion.isEmpty());

		index = new JarIndex(new ReferencedEntryPool());
		index.indexJar(new ParsedJar(new JarFile(newJar)), true);

		newMappings = new Mappings();

		if(historyFile.exists()){
			System.out.println("Reading history file");
			mappingHistory = MappingHistory.readHistory(historyFile);
		} else {
			mappingHistory = MappingHistory.newMappingsHistory();
		}

		if(oldMappingsFile != null && oldMappingsFile.exists()){
			System.out.println("Reading old mappings");
			MappingsEnigmaReader mappingReader = new MappingsEnigmaReader();
			try {
				oldMappings = mappingReader.read(oldMappingsFile);
			} catch (MappingParseException e) {
				throw new RuntimeException("Failed to read input mappings", e);
			}
		}

		if(matchesFile != null && matchesFile.exists()){
			matches = new Matches();
			System.out.println("Reading matches");
			matches.read(matchesFile);
			System.out.println("Found " + matches.classMatches.size() + " matched classes");
			System.out.println("Found " + matches.methodMatches.size() + " matched methods");
			System.out.println("Found " + matches.fieldMatches.size() + " matched fields");
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

		if(!simulate){
			System.out.println("Writing history to disk");
			mappingHistory.writeToFile(historyFile);

			System.out.println("Exporting new mappings");
			MappingsEnigmaWriter mappingWriter = new MappingsEnigmaWriter();
			mappingWriter.write(outputMappingsFile, newMappings, false);
		}
	}

	private void handleClass(ClassEntry classEntry) {
		if (!classEntry.getClassName().contains("/")) { //Skip everything already in a package
			String match = getClassMatch(classEntry);
			if(match == null){
				handleNewClass(classEntry);
			} else {
				handleMatchedClass(classEntry, match);
			}

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
			String newClassName = mappingHistory.generateClassName(newMinecraftVersion);
			System.out.println("NC: " + classEntry.getClassName() + " -> " + newClassName);
			newMappings.addClassMapping(new ClassMapping(classEntry.getClassName(), packageName + "/" + newClassName));
		} catch (MappingConflict mappingConflict) {
			throw new RuntimeException("Mappings failed to apply", mappingConflict);
		}
	}

	private void handleMatchedClass(ClassEntry entry, String oldName){
		ClassMapping classMapping = oldMappings.getClassByObf(oldName);
		System.out.println("MC: " + entry.getClassName() + " -> " + classMapping.getDeobfName());
		try {
			newMappings.addClassMapping(new ClassMapping(entry.getClassName(), packageName + "/" + classMapping.getDeobfName()));
		} catch (MappingConflict mappingConflict) {
			throw new RuntimeException("Mappings failed to apply", mappingConflict);
		}
	}

	private String getClassMatch(ClassEntry classEntry){
		if(matches != null){
			if(matches.classMatches.containsValue(classEntry.getClassName())){
				String match = matches.classMatches.inverse().get(classEntry.getClassName());
				return match;
			}
		}
		return null;
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
		if(methodEntry.getAccess().isSynthetic()){
			return;
		}
		//TODO this is a horrible way to figure out if it the entry is mapped
		if (methodEntry.getOwnerClassEntry() != null
			&& methodEntry.getOwnerClassEntry().getPackageName() != null
			&& methodEntry.getOwnerClassEntry().getPackageName().contains("net/minecraft")
			&& methodEntry.getName().length() > 2) {
			return;
		}
		String match = getMethodMatch(methodEntry);
		if(match != null){
			handleMatchedMethod(methodEntry, match);
		} else {
			handleNewMethod(methodEntry);
		}
	}

	private void handleNewMethod(MethodDefEntry methodEntry) {
		String signature = methodEntry.getDesc().toString();
		String newMethodName = mappingHistory.generateMethodName(signature, newMinecraftVersion);
		System.out.println("NM: " + methodEntry.getName() + signature + " -> " + newMethodName + signature);

		newMappings.getClassByObf(methodEntry.getOwnerClassEntry()).addMethodMapping(new MethodMapping(methodEntry.getName(), methodEntry.getDesc(), newMethodName));
	}

	private void handleMatchedMethod(MethodDefEntry methodEntry, String match) {
		String oldClassName = match.substring(0, match.indexOf("."));
		ClassMapping oldClass = oldMappings.getClassByObf(oldClassName);
		String oldMethodDesc = match.substring(match.indexOf("("));
		String s1 = match.substring(match.indexOf(".") + 1);

		String oldMethodName = s1.substring(0, s1.indexOf("("));
		MethodMapping oldMapping = oldClass.getMethodByObf(oldMethodName, new MethodDescriptor(oldMethodDesc));

		String signature = methodEntry.getDesc().toString();

		System.out.println("MM: " + methodEntry.getName() + signature + " -> " + oldMapping.getDeobfName());
		newMappings.getClassByObf(methodEntry.getOwnerClassEntry()).addMethodMapping(new MethodMapping(methodEntry.getName(), methodEntry.getDesc(), oldMapping.getDeobfName()));
	}

	private String getMethodMatch(MethodDefEntry methodEntry){
		if(matches != null){
			String className = methodEntry.getOwnerClassEntry().getClassName();
			String desc = methodEntry.getDesc().toString();
			String lookup = className + "." + methodEntry.getName() + desc;
			if(matches.methodMatches.containsValue(lookup)){
				String match = matches.methodMatches.inverse().get(lookup);
				return match;
			}
		}
		return null;
	}

	private void handleField(FieldDefEntry fieldEntry) {
		if (!Util.isObfuscatedIdentifier(fieldEntry, true, index)) {
			return;
		}
		if(fieldEntry.getAccess().isSynthetic()){
			return;
		}
		//TODO this is a horrible way to figure out if it the entry is mapped
		if (fieldEntry.getOwnerClassEntry() != null
			&& fieldEntry.getOwnerClassEntry().getPackageName() != null
			&& fieldEntry.getOwnerClassEntry().getPackageName().contains("net/minecraft")
			&& fieldEntry.getName().length() > 2) {
			return;
		}
		String match = getFieldMatch(fieldEntry);
		if(match != null){
			handleFieldMatch(fieldEntry, match);
		} else {
			handleNewField(fieldEntry);
		}
	}

	private void handleNewField(FieldDefEntry fieldEntry) {
		String signature = fieldEntry.getDesc().toString();
		String newFieldName = mappingHistory.generateFieldName(signature, newMinecraftVersion);
		System.out.println("NF: " + fieldEntry.getName() + signature + " -> " + newFieldName);
		newMappings.getClassByObf(fieldEntry.getOwnerClassEntry()).addFieldMapping(new FieldMapping(fieldEntry.getName(), fieldEntry.getDesc(), newFieldName, Mappings.EntryModifier.UNCHANGED));
	}

	private void handleFieldMatch(FieldDefEntry fieldEntry, String match){
		String oldClassName = match.substring(0, match.indexOf("."));
		ClassMapping oldClass = oldMappings.getClassByObf(oldClassName);
		String oldFieldDesc = match.substring(match.indexOf(";;") + 2);
		String s1 = match.substring(match.indexOf(".") + 1);
		String oldFieldName = s1.substring(0, s1.indexOf(";;"));
		FieldMapping oldMapping = oldClass.getFieldByObf(oldFieldName, new TypeDescriptor(oldFieldDesc));

		System.out.println("MF: " + fieldEntry.getName() + fieldEntry.getDesc() + " -> " + oldMapping.getDeobfName());
		newMappings.getClassByObf(fieldEntry.getOwnerClassEntry()).addFieldMapping(new FieldMapping(fieldEntry.getName(), fieldEntry.getDesc(), oldMapping.getDeobfName(), Mappings.EntryModifier.UNCHANGED));

	}

	private String getFieldMatch(FieldDefEntry fieldDefEntry){
		if(matches != null){
			String className = fieldDefEntry.getOwnerClassEntry().getClassName();
			String lookup = className + "." + fieldDefEntry.getName() + ";;" + fieldDefEntry.getDesc().toString();
			if(matches.fieldMatches.containsValue(lookup)){
				String match = matches.fieldMatches.inverse().get(lookup);
				return match;
			}
		}
		return null;
	}

	public Generate setNewJar(File newJar) {
		this.newJar = newJar;
		return this;
	}

	public Generate setOldJar(File oldJar) {
		this.oldJar = oldJar;
		return this;
	}

	public Generate setOldMappingsFile(File oldMappingsFile) {
		this.oldMappingsFile = oldMappingsFile;
		return this;
	}

	public Generate setMatchesFile(File matchesFile) {
		this.matchesFile = matchesFile;
		return this;
	}

	public Generate setOutputMappingsFile(File outputMappings) {
		this.outputMappingsFile = outputMappings;
		return this;
	}

	public Generate setHistoryFile(File historyFile) {
		this.historyFile = historyFile;
		return this;
	}

	public Generate resetHistory(){
		historyFile.delete();
		return this;
	}

	public Generate setNewMinecraftVersion(String newMinecraftVersion) {
		this.newMinecraftVersion = newMinecraftVersion;
		return this;
	}

	public Generate setPackageName(String packageName) {
		this.packageName = packageName;
		return this;
	}

	public Generate simulate(){
		simulate = true;
		return this;
	}
}
