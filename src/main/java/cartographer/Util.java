package cartographer;

import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.mapping.entry.Entry;
import cuchaz.enigma.mapping.entry.MethodEntry;

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

	public static class ClassMatchResponse {
		public boolean isNew;
		public String oldName;
		public String currentName;
		public String intermediateName;

		public ClassMatchResponse(boolean isNew, String oldName, String currentName, String intermediateName) {
			this.isNew = isNew;
			this.oldName = oldName;
			this.currentName = currentName;
			this.intermediateName = intermediateName;
		}
	}

}
