package cartographer;

import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.mapping.entry.*;

import java.util.HashSet;
import java.util.Set;

public class Util {

	//Taken from enigma
	public static boolean isObfuscatedIdentifier(Entry obfEntry, boolean hack, JarIndex jarIndex) {
		if (obfEntry instanceof MethodEntry) {
			// HACKHACK: Object methods are not obfuscated identifiers
			MethodEntry obfMethodEntry = (MethodEntry) obfEntry;
			String name = obfMethodEntry.getName();
			String sig = obfMethodEntry.getDesc().toString();
			if (name.equals("clone") && sig.equals("()Ljava/lang/Object;")) {
				return false;
			} else if (name.equals("equals") && sig.equals("(Ljava/lang/Object;)Z")) {
				return false;
			} else if (name.equals("finalize") && sig.equals("()V")) {
				return false;
			} else if (name.equals("getClass") && sig.equals("()Ljava/lang/Class;")) {
				return false;
			} else if (name.equals("hashCode") && sig.equals("()I")) {
				return false;
			} else if (name.equals("notify") && sig.equals("()V")) {
				return false;
			} else if (name.equals("notifyAll") && sig.equals("()V")) {
				return false;
			} else if (name.equals("toString") && sig.equals("()Ljava/lang/String;")) {
				return false;
			} else if (name.equals("wait") && sig.equals("()V")) {
				return false;
			} else if (name.equals("wait") && sig.equals("(J)V")) {
				return false;
			} else if (name.equals("wait") && sig.equals("(JI)V")) {
				return false;
			}

			// FIXME: HACK EVEN MORE HACK!
			if (hack && jarIndex.containsObfEntry(obfEntry.getOwnerClassEntry()))
				return true;
		}

		return jarIndex.containsObfEntry(obfEntry);
	}

	public static boolean isMethodProvider(ClassEntry classObfEntry, MethodEntry methodEntry, ReferencedEntryPool entryPool, JarIndex index) {
		Set<ClassEntry> classEntries = new HashSet<>();
		addAllPotentialAncestors(classEntries, classObfEntry, entryPool, index);

		for (ClassEntry parentEntry : classEntries) {
			MethodEntry ancestorMethodEntry = entryPool.getMethod(parentEntry, methodEntry.getName(), methodEntry.getDesc());
			if (index.containsObfMethod(ancestorMethodEntry)) {
				return false;
			}
		}

		return true;
	}

	public static void addAllPotentialAncestors(Set<ClassEntry> classEntries, ClassEntry classObfEntry, ReferencedEntryPool entryPool, JarIndex index) {
		for (ClassEntry interfaceEntry : index.getTranslationIndex().getInterfaces(classObfEntry)) {
			if (classEntries.add(interfaceEntry)) {
				addAllPotentialAncestors(classEntries, interfaceEntry, entryPool, index);
			}
		}
		ClassEntry superClassEntry = index.getTranslationIndex().getSuperclass(classObfEntry);
		if (superClassEntry != null && classEntries.add(superClassEntry)) {
			addAllPotentialAncestors(classEntries, superClassEntry, entryPool, index);
		}
	}

	public static String translate(MethodDefEntry methodEntry) {
		String className = methodEntry.getOwnerClassEntry().getClassName();
		String desc = methodEntry.getDesc().toString();
		return className + "." + methodEntry.getName() + desc;
	}

}
