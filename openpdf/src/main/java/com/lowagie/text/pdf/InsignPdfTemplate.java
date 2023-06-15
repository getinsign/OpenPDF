package com.lowagie.text.pdf;

import java.io.IOException;
import java.util.logging.Logger;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;

public class InsignPdfTemplate  extends PdfTemplate{
	private static Logger logger = Logger.getLogger(InsignPdfTemplate.class.getName());
	private String role;
	private String displayname;
	
	public InsignPdfTemplate(PdfWriter writer, String role, String displayname, Image image, Rectangle pagerect) throws DocumentException {
		super(writer);
		this.role = role;
		this.displayname = displayname;
		this.addImage(image);
		this.setWidth(pagerect.getWidth());
		this.setHeight(pagerect.getHeight());
		
	}
	
	@Override
	PdfStream getFormXObject(int compressionLevel) throws IOException {
		PdfStream ret = super.getFormXObject(compressionLevel);
		PdfDictionary dict = new PdfDictionary();
		dict.put(new PdfName("INSI_Role"), new PdfString(role));
		dict.put(new PdfName("INSI_DispName"), new PdfString(displayname));
		ret.put(new PdfName("INSI_Meta"), dict);
		return ret;
	}
	
}
