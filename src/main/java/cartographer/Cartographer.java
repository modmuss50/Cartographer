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
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

public class Cartographer {

	File oldJar;
	File newJar;

	private Mappings oldMappings;
	private Mappings newMappings;
	private Matches matches;

	File oldMappingsFile;
	File outputMappingsFile;
	File historyFile;
	File matchesFile;

	String packageName = "net/minecraft";

	private MappingHistory mappingHistory;
	private JarIndex index;

	private boolean simulate = false;

	public void start() throws IOException {
		Validate.notNull(newJar);
		Validate.notNull(outputMappingsFile);
		Validate.notNull(historyFile);
		Validate.isTrue(!packageName.isEmpty());

		index = new JarIndex(new ReferencedEntryPool());
		index.indexJar(new ParsedJar(new JarFile(newJar)), true);

		newMappings = new Mappings();

		if (historyFile.exists()) {
			System.out.println("Reading history file");
			mappingHistory = MappingHistory.readHistory(historyFile);
		} else {
			mappingHistory = MappingHistory.newMappingsHistory();
		}

		if (oldMappingsFile != null && oldMappingsFile.exists()) {
			System.out.println("Reading old mappings");
			MappingsEnigmaReader mappingReader = new MappingsEnigmaReader();
			try {
				oldMappings = mappingReader.read(oldMappingsFile);
			} catch (MappingParseException e) {
				throw new RuntimeException("Failed to read input mappings", e);
			}
		}

		if (matchesFile != null && matchesFile.exists()) {
			matches = new Matches();
			System.out.println("Reading matches");
			matches.read(matchesFile);
			System.out.println("Found " + matches.classMatches.size() + " matched classes");
			System.out.println("Found " + matches.methodMatches.size() + " matched methods");
			System.out.println("Found " + matches.methodArgMatches.size() + " matched method args");
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

		if (!simulate) {
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
			if (match == null) {
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
			String newClassName = mappingHistory.generateClassName();
			//	System.out.println("NC: " + classEntry.getClassName() + " -> " + newClassName);
			newMappings.addClassMapping(new ClassMapping(classEntry.getClassName(), packageName + "/" + newClassName));
		} catch (MappingConflict mappingConflict) {
			throw new RuntimeException("Mappings failed to apply", mappingConflict);
		}
	}

	private void handleMatchedClass(ClassEntry entry, String oldName) {
		ClassMapping classMapping = oldMappings.getClassByObf(oldName);
		//System.out.println("MC: " + entry.getClassName() + " -> " + classMapping.getDeobfName());
		try {
			newMappings.addClassMapping(new ClassMapping(entry.getClassName(), packageName + "/" + classMapping.getDeobfName()));
		} catch (MappingConflict mappingConflict) {
			throw new RuntimeException("Mappings failed to apply", mappingConflict);
		}
	}

	private String getClassMatch(ClassEntry classEntry) {
		if (matches != null) {
			if (matches.classMatches.containsValue(classEntry.getClassName())) {
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
		if (methodEntry.getAccess().isSynthetic()) {
			return;
		}
		//TODO this is a horrible way to figure out if it the entry is mapped
		if (methodEntry.getOwnerClassEntry() != null
			&& methodEntry.getOwnerClassEntry().getPackageName() != null
			&& methodEntry.getOwnerClassEntry().getPackageName().contains("net/minecraft")
			&& methodEntry.getName().length() > 2) {
			return;
		}
		Pair<MethodMapping, MethodMapping> mapping;
		String match = getMethodMatch(methodEntry);
		if (match != null) {
			mapping = handleMatchedMethod(methodEntry, match);
		} else {
			mapping = handleNewMethod(methodEntry);
		}
		handleMethodArgs(methodEntry, mapping);
	}

	private Pair<MethodMapping, MethodMapping> handleNewMethod(MethodDefEntry methodEntry) {
		String signature = methodEntry.getDesc().toString();
		String newMethodName = mappingHistory.generateMethodName(signature);
		System.out.println("NM: " + methodEntry.getName() + signature + " -> " + newMethodName + signature);

		MethodMapping mapping = new MethodMapping(methodEntry.getName(), methodEntry.getDesc(), newMethodName);
		newMappings.getClassByObf(methodEntry.getOwnerClassEntry()).addMethodMapping(mapping);
		return Pair.of(null, mapping);
	}

	private Pair<MethodMapping, MethodMapping> handleMatchedMethod(MethodDefEntry methodEntry, String match) {
		String oldClassName = match.substring(0, match.indexOf("."));
		ClassMapping oldClass = oldMappings.getClassByObf(oldClassName);
		String oldMethodDesc = match.substring(match.indexOf("("));
		String s1 = match.substring(match.indexOf(".") + 1);

		String oldMethodName = s1.substring(0, s1.indexOf("("));
		MethodMapping oldMapping = oldClass.getMethodByObf(oldMethodName, new MethodDescriptor(oldMethodDesc));

		String signature = methodEntry.getDesc().toString();

		System.out.println("MM: " + methodEntry.getName() + signature + " -> " + oldMapping.getDeobfName());
		MethodMapping mapping = new MethodMapping(methodEntry.getName(), methodEntry.getDesc(), oldMapping.getDeobfName());
		newMappings.getClassByObf(methodEntry.getOwnerClassEntry()).addMethodMapping(mapping);
		return Pair.of(oldMapping, mapping);
	}

	private String getMethodMatch(MethodDefEntry methodEntry) {
		if (matches != null) {
			String lookup = Util.translate(methodEntry);
			if (matches.methodMatches.containsValue(lookup)) {
				String match = matches.methodMatches.inverse().get(lookup);
				return match;
			}
		}
		return null;
	}

	private void handleMethodArgs(MethodDefEntry methodEntry, Pair<MethodMapping, MethodMapping> methodMappingPair) {
		int arguments = methodEntry.getDesc().getArgumentDescs().size();
		MethodMapping oldMapping = methodMappingPair.getLeft();
		MethodMapping newMapping = methodMappingPair.getRight();

		Map<Integer, LocalVariableMapping> oldArgMappings = new HashMap<>();
		if (oldMapping != null) {
			oldMapping.arguments().forEach(localVariableMapping -> oldArgMappings.put(localVariableMapping.getIndex(), localVariableMapping));
		}

		for (int arg = 0; arg < arguments; arg++) {
			String match = getMethodArgMatch(methodEntry, arg);
			if (match != null && oldMapping != null) {
				int oldArgPos = Integer.parseInt(match.substring(match.indexOf("#") + 1));
				String oldName = oldArgMappings.get(oldArgPos).getName();
				try {
					newMapping.addArgumentMapping(new LocalVariableMapping(arg, oldName));
				} catch (MappingConflict mappingConflict) {
					throw new RuntimeException("Failed to map arg", mappingConflict);
				}
				System.out.println("\tMP: " + newMapping.getDeobfName() + "_" + arg + " -> " + oldName);
			} else {
				String newName = mappingHistory.generateArgName(newMapping, arg);
				try {
					newMapping.addArgumentMapping(new LocalVariableMapping(arg, newName));
				} catch (MappingConflict mappingConflict) {
					throw new RuntimeException("Failed to map arg", mappingConflict);
				}
				System.out.println("\tNP: " + newMapping.getDeobfName() + "_" + arg + " -> " + newName);
			}
		}
	}

	private String getMethodArgMatch(MethodDefEntry methodEntry, int index) {
		if (matches != null) {
			String lookup = Util.translate(methodEntry) + "#" + index;
			if (matches.methodArgMatches.containsValue(lookup)) {
				String match = matches.methodArgMatches.inverse().get(lookup);
				return match;
			}
		}
		return null;
	}

	private void handleField(FieldDefEntry fieldEntry) {
		if (!Util.isObfuscatedIdentifier(fieldEntry, true, index)) {
			return;
		}
		if (fieldEntry.getAccess().isSynthetic()) {
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
		if (match != null) {
			handleFieldMatch(fieldEntry, match);
		} else {
			handleNewField(fieldEntry);
		}
	}

	private void handleNewField(FieldDefEntry fieldEntry) {
		String signature = fieldEntry.getDesc().toString();
		String newFieldName = mappingHistory.generateFieldName(signature);
		//System.out.println("NF: " + fieldEntry.getName() + signature + " -> " + newFieldName);
		newMappings.getClassByObf(fieldEntry.getOwnerClassEntry()).addFieldMapping(new FieldMapping(fieldEntry.getName(), fieldEntry.getDesc(), newFieldName, Mappings.EntryModifier.UNCHANGED));
	}

	private void handleFieldMatch(FieldDefEntry fieldEntry, String match) {
		String oldClassName = match.substring(0, match.indexOf("."));
		ClassMapping oldClass = oldMappings.getClassByObf(oldClassName);
		String oldFieldDesc = match.substring(match.indexOf(";;") + 2);
		String s1 = match.substring(match.indexOf(".") + 1);
		String oldFieldName = s1.substring(0, s1.indexOf(";;"));
		FieldMapping oldMapping = oldClass.getFieldByObf(oldFieldName, new TypeDescriptor(oldFieldDesc));

		//System.out.println("MF: " + fieldEntry.getName() + fieldEntry.getDesc() + " -> " + oldMapping.getDeobfName());
		newMappings.getClassByObf(fieldEntry.getOwnerClassEntry()).addFieldMapping(new FieldMapping(fieldEntry.getName(), fieldEntry.getDesc(), oldMapping.getDeobfName(), Mappings.EntryModifier.UNCHANGED));

	}

	private String getFieldMatch(FieldDefEntry fieldDefEntry) {
		if (matches != null) {
			String className = fieldDefEntry.getOwnerClassEntry().getClassName();
			String lookup = className + "." + fieldDefEntry.getName() + ";;" + fieldDefEntry.getDesc().toString();
			if (matches.fieldMatches.containsValue(lookup)) {
				String match = matches.fieldMatches.inverse().get(lookup);
				return match;
			}
		}
		return null;
	}

	public Cartographer setNewJar(File newJar) {
		this.newJar = newJar;
		return this;
	}

	public Cartographer setOldJar(File oldJar) {
		this.oldJar = oldJar;
		return this;
	}

	public Cartographer setOldMappingsFile(File oldMappingsFile) {
		this.oldMappingsFile = oldMappingsFile;
		return this;
	}

	public Cartographer setMatchesFile(File matchesFile) {
		this.matchesFile = matchesFile;
		return this;
	}

	public Cartographer setOutputMappingsFile(File outputMappings) {
		this.outputMappingsFile = outputMappings;
		return this;
	}

	public Cartographer setHistoryFile(File historyFile) {
		this.historyFile = historyFile;
		return this;
	}

	public Cartographer resetHistory() {
		historyFile.delete();
		return this;
	}

	public Cartographer setPackageName(String packageName) {
		this.packageName = packageName;
		return this;
	}

	public Cartographer simulate() {
		simulate = true;
		return this;
	}
}
