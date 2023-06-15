/**
 * @author RolandW
 * @since 18.04.2013
 */
package com.lowagie.text.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.lowagie.text.DocumentException;
import com.lowagie.text.ExceptionConverter;
import com.lowagie.text.xml.xmp.XmpWriter;

import lombok.Cleanup;

/**
 * @author RolandW
 * @since 18.04.2013
 */
public class InsignPdfStamper extends PdfStamper {
    private static Logger logger = Logger.getLogger(InsignPdfStamper.class.getName());

	private InsignPdfSignatureAppearance sigApp;

	private boolean hasSignature;
	 private boolean cleanMetadata = false;
	
	
	
    public InsignPdfStamper(final PdfReader reader, final OutputStream os, final char pdfVersion, final boolean append, String appversion) throws DocumentException, IOException {
    	super(new PdfReader(reader), new ByteBuffer());//RW: Wir müssen den Base Ctor austricksen (sonst PDF tampered)
        stamper = new InsignPdfStamperImp(reader, os, pdfVersion, append, appversion);
        sigApp = new InsignPdfSignatureAppearance(stamper);
    }

	public InsignPdfStamper(final InsignPdfReader reader, final ByteArrayOutputStream out, String appversion) throws DocumentException, IOException {
		super(new PdfReader(reader), new ByteBuffer());
		stamper = new InsignPdfStamperImp(reader, out, '\0', false,appversion);
	}
	
    @Override
	public InsignAcroFields getAcroFields() {
        return (InsignAcroFields) stamper.getAcroFields();
    }

	public void addDirectTemplateSimple(PdfTemplate template, PdfName forcedName) {
		getWriter().addDirectTemplateSimple(template,forcedName);
	}
	
    @Override
	public PdfSignatureAppearance getSignatureAppearance() {
        return sigApp;
    }
    
    public static InsignPdfStamper createSignature(PdfReader reader, OutputStream os, char pdfVersion, boolean append, String appVersion) throws DocumentException, IOException {
    	InsignPdfStamper stp;
    	@Cleanup
        ByteBuffer bout = new ByteBuffer();
        stp = new InsignPdfStamper(reader, bout, pdfVersion, append, appVersion);
        stp.sigApp = new InsignPdfSignatureAppearance(stp.stamper);
        stp.sigApp.setSigout(bout);
        stp.getSignatureAppearance().setOriginalout(os);
        stp.getSignatureAppearance().setStamper(stp);
        stp.hasSignature = true;
      //hasSignature in der Basisklasse setzen:
        Field oFieldContext = null;
		try {
			oFieldContext = PdfStamper.class.getDeclaredField("hasSignature");
			oFieldContext.setAccessible(true);
			oFieldContext.setBoolean(stp, true);
		} catch (Exception e){
			throw new RuntimeException("Error@Reflection", e);
		}
        PdfDictionary catalog = reader.getCatalog();
        PdfDictionary acroForm = (PdfDictionary)PdfReader.getPdfObject(catalog.get(PdfName.ACROFORM), catalog);
        if (acroForm != null) {
            acroForm.remove(PdfName.NEEDAPPEARANCES);
            stp.stamper.markUsed(acroForm);
        }
        return stp;
    }
    

    
    @Override
	public void cleanMetadata() {
        Map<String, String> meta = new HashMap<>();
        meta.put("Title", null);
        meta.put("Author", null);
        meta.put("Subject", null);
        meta.put("Producer", null);
        meta.put("Keywords", null);
        meta.put("Creator", null);
        meta.put("CreationDate", null);
        meta.put("ModDate",null);
        setInfoDictionary(meta);
        this.cleanMetadata = true;
      }
    
    @Override
	public void close() throws DocumentException, IOException {
        if (!hasSignature) {
            if (cleanMetadata && stamper.xmpMetadata == null) {
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              try {
                XmpWriter writer = new XmpWriter(baos, getMoreInfo());
                writer.close();
                stamper.setXmpMetadata(baos.toByteArray());
              }
              catch (IOException ignore) {
                // ignore exception
              }
            }
            stamper.close(getMoreInfo());
            return;
        }
        sigApp.preClose();
        InsignPdfSigGenericPKCS sig = sigApp.getInsignSigStandard();
        PdfLiteral lit = (PdfLiteral)sig.get(PdfName.CONTENTS);
        int totalBuf = (lit.getPosLength() - 2) / 2;
        byte[] buf = new byte[8192];
        int n;
        InputStream inp = sigApp.getRangeStream();
        try {
            while ((n = inp.read(buf)) > 0) {
                sig.getSigner().update(buf, 0, n);
            }
        }
        catch (SignatureException se) {
            throw new ExceptionConverter(se);
        }
        buf = new byte[totalBuf];
        byte[] bsig = sig.getSignerContents();
        System.arraycopy(bsig, 0, buf, 0, bsig.length);
        PdfString str = new PdfString(buf);
        str.setHexWriting(true);
        PdfDictionary dic = new PdfDictionary();
        dic.put(PdfName.CONTENTS, str);
        sigApp.close(dic);
        stamper.reader.close();
    }

}
