package com.lowagie.text.pdf;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;

import com.lowagie.text.Chunk;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;

public class InsignTextField extends TextField{

	
	
	 

	public InsignTextField(PdfWriter writer, Rectangle box, String fieldName) {
		super(writer, box, fieldName);
	}

	
	/*Same as TextField.getListAppearence except
	 * a) removed first = topChoice as it gives the wrong selection in the display 
	 * b) changed setColorFill to light background instead of darkblue so that it looks similar to the default selection when we do not set value with code
	 * c) removed the textCol=GrayColor.GRAYWHITE since the background is light now 
	 */
	@Override
	PdfAppearance getListAppearance() throws IOException, DocumentException {
		PdfAppearance app = getBorderAppearance();
		 String[] choices =getChoices();
		 String[] choiceExports=getChoiceExports();
 	     ArrayList choiceSelections = gteChoiceSelections();
 	    int topFirst;
   	if (choices == null || choices.length == 0) {
           return app;
       }
       app.beginVariableText();
     //  int topChoice = getTopChoice();

       BaseFont ufont = getRealFont();
       float usize = fontSize;
       if (usize == 0)
           usize = 12;

       boolean borderExtra = borderStyle == PdfBorderDictionary.STYLE_BEVELED || borderStyle == PdfBorderDictionary.STYLE_INSET;
       float h = box.getHeight() - borderWidth * 2;
       float offsetX = borderWidth;
       if (borderExtra) {
           h -= borderWidth * 2;
           offsetX *= 2;
       }
       
       float leading = ufont.getFontDescriptor(BaseFont.BBOXURY, usize) - ufont.getFontDescriptor(BaseFont.BBOXLLY, usize);
       int maxFit = (int)(h / leading) + 1;
       int first = 0;
       int last = 0;
     //  first = topChoice;
       last = first + maxFit;
       if (last > choices.length)
           last = choices.length;
       topFirst = first;
       app.saveState();
       app.rectangle(offsetX, offsetX, box.getWidth() - 2 * offsetX, box.getHeight() - 2 * offsetX);
       app.clip();
       app.newPath();
     Color fcolor = (textColor == null) ? GrayColor.GRAYBLACK : textColor;
       
       
       // background boxes for selected value[s]
    
     	app.setColorFill(new Color(153,193,218)); //  app.setColorFill(new Color(10, 36, 106));
      for (int curVal = 0; curVal < choiceSelections.size(); ++curVal) {
       	int curChoice = ((Integer)choiceSelections.get( curVal )).intValue();
       	// only draw selections within our display range... not strictly necessary with 
       	// that clipping rect from above, but it certainly doesn't hurt either 
       	if (curChoice >= first && curChoice <= last) {
       		app.rectangle(offsetX, offsetX + h - (curChoice - first + 1) * leading, box.getWidth() - 2 * offsetX, leading);
       		app.fill();
       	}
       }
       float xp = offsetX * 2;
       float yp = offsetX + h - ufont.getFontDescriptor(BaseFont.BBOXURY, usize);
      for (int idx = first; idx < last; ++idx, yp -= leading) {
           String ptext = choices[idx];
           int rtl = checkRTL(ptext) ? PdfWriter.RUN_DIRECTION_LTR : PdfWriter.RUN_DIRECTION_NO_BIDI;
           ptext = removeCRLF(ptext);
           // highlight selected values against their (presumably) darker background
        //  Color textCol = (choiceSelections.contains( new Integer( idx ))) ? GrayColor.GRAYWHITE : fcolor;
           Phrase phrase = composePhrase(ptext, ufont, fcolor, usize); //changed textCol to fcolor
           ColumnText.showTextAligned(app, Element.ALIGN_LEFT, phrase, xp, yp, 0, rtl, 0);
       }
       app.restoreState();
       app.endVariableText();
       return app;
		
	}
  
	// same as TextField checkRTL
	private static boolean checkRTL(String text) {
        if (text == null || text.length() == 0)
            return false;
        char[] cc = text.toCharArray();
        for (int k = 0; k < cc.length; ++k) {
            int c = cc[k];
            if (c >= 0x590 && c < 0x0780)
                return true;
        }
        return false;
    }
	
	// same as TextField.composePhrase with initialization done
   private Phrase composePhrase(String text, BaseFont ufont, Color color, float fontSize) {
        Phrase phrase = null;
     BaseFont   extensionFont  =  getExtensionFont();
     ArrayList substitutionFonts =getSubstitutionFonts();
        if (extensionFont == null && (substitutionFonts == null || substitutionFonts.isEmpty()))
            phrase = new Phrase(new Chunk(text, new Font(ufont, fontSize, 0, color)));
        else {
            FontSelector fs = new FontSelector();
            fs.addFont(new Font(ufont, fontSize, 0, color));
            if (extensionFont != null)
                fs.addFont(new Font(extensionFont, fontSize, 0, color));
            if (substitutionFonts != null) {
                for (int k = 0; k < substitutionFonts.size(); ++k)
                    fs.addFont(new Font((BaseFont)substitutionFonts.get(k), fontSize, 0, color));
            }
            phrase = fs.process(text);
        }
        return phrase;
    }
   
   
  
}
