package de.is2.sign.utils;

import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfObject;

public class PdfHelper {
	
	public static PdfObject getValueFromTree(PdfDictionary root, String[] path) {
		if (root == null) {
			return null;
		}
		PdfObject ret = null;
		for (int i = 0; i < path.length - 1; i++) {
			root = root.getAsDict(new PdfName(path[i]));
			if (root == null) {
				return null;
			}
		}
		ret = root.get(new PdfName(path[path.length - 1]));
		return ret;
	}

}
