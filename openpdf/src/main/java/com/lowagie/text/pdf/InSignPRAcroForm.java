/**
 * @author roland.wachinger
 * @since 17.12.2019
 */
package com.lowagie.text.pdf;

import java.util.Iterator;

public class InSignPRAcroForm extends PRAcroForm {

	public InSignPRAcroForm(PdfReader reader) {
		super(reader);
	}
	
	@Override
	protected void iterateFields(PdfArray fieldlist, PRIndirectReference fieldDict, String title) {
        for (Iterator it = fieldlist.listIterator(); it.hasNext();) {
            PRIndirectReference ref = (PRIndirectReference)it.next();
            PdfDictionary dict = (PdfDictionary) PdfReader.getPdfObjectRelease(ref);
            if (dict == null) {//RW: Added null check
            	continue;
            }
            // if we are not a field dictionary, pass our parent's values
            PRIndirectReference myFieldDict = fieldDict;
            String myTitle = title;
            
            PdfString tField = (PdfString)dict.get(PdfName.T);
            boolean isFieldDict = tField != null;
            
            if (isFieldDict) {
                myFieldDict = ref;
                if (title == null) myTitle = tField.toString();
                else myTitle = title + '.' + tField.toString();
            }
            
            PdfArray kids = (PdfArray)dict.get(PdfName.KIDS);
            if (kids != null) {
                pushAttrib(dict);
                iterateFields(kids, myFieldDict, myTitle);
                stack.remove(stack.size() - 1);   // pop
            }
            else {          // leaf node
                if (myFieldDict != null) {
                    PdfDictionary mergedDict = (PdfDictionary)stack.get(stack.size() - 1);
                    if (isFieldDict)
                        mergedDict = mergeAttrib(mergedDict, dict);
                    
                    mergedDict.put(PdfName.T, new PdfString(myTitle));
                    FieldInformation fi = new FieldInformation(myTitle, mergedDict, myFieldDict);
                    fields.add(fi);
                    fieldByName.put(myTitle, fi);
                }
            }
        }
    }

}
