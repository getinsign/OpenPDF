package com.lowagie.text.pdf;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.AcroFields.Item;

import de.is2.sign.keydata.KeyData;
import lombok.Cleanup;

/**
 * VF:
 * PdfSignatureAppearance
 * wurde ueberschrieben da sonst der PDF/a 2b Standard nicht gewaehrleistet werden kann
 * Diesen benoetigt die Generali zur Archivierung ihrer PDFs.
 *
 */
public class InsignPdfSignatureAppearance extends PdfSignatureAppearance {
    private static Logger logger = Logger.getLogger(InsignPdfSignatureAppearance.class.getName());
	private long range[];
	private int boutLen;
	private byte bout[];
	private byte externalDigest[];
	private byte externalRSAdata[];

	private String digestEncryptionAlgorithm;
	private File tempFile;
	private PdfStamper stamper;
	private RandomAccessFile raf;
	private SignatureEvent signatureEvent;
	private HashMap exclusionLocations;
	private PdfDictionary cryptoDictionary;
	private PdfStamperImp writer;
	
	private KeyData certKeyData;
	private InsignPdfSigGenericPKCS sigStandard;
	
	public InsignPdfSignatureAppearance(PdfStamperImp writer) {
		super(writer);
		this.writer = writer;
	}
	
	protected void setObjectInBaseClass(Object obj, String filedname) {
		Field oFieldContext = null;
        try {
			oFieldContext = PdfSignatureAppearance.class.getDeclaredField(filedname);
			oFieldContext.setAccessible(true);
			oFieldContext.set(this, obj);
		} catch (Exception e){
			throw new RuntimeException("Error@Reflection", e);
		}
        finally {
			if (oFieldContext != null) {
				oFieldContext.setAccessible(false);
			}
		}
	}
	
	protected Object getPrivateBaseClassField(String fieldName) {
		Field oFieldContext = null;
		try {
			oFieldContext = PdfSignatureAppearance.class.getDeclaredField(fieldName);
			oFieldContext.setAccessible(true);
			return oFieldContext.get(this);
		}
		catch (final Exception oEx) {
			throw new RuntimeException("Error@Reflection", oEx);
		}
		finally {
			if (oFieldContext != null) {
				oFieldContext.setAccessible(false);
			}
		}
	}
	
	private void setPreClosedinBaseClass() {
        Field oFieldContext = null;
		try {
			oFieldContext = PdfSignatureAppearance.class.getDeclaredField("preClosed");
			oFieldContext.setAccessible(true);
			oFieldContext.setBoolean(this, true);
		} catch (Exception e){
			throw new RuntimeException("Error@Reflection", e);
		}
	}
	
	
	@Override
	public void preClose(Map<PdfName, Integer> exclusionSizes) throws IOException, DocumentException {
		
		boolean preClosed = (Boolean) getPrivateBaseClassField("preClosed");
		externalDigest = (byte[]) getPrivateBaseClassField("externalDigest");
		externalRSAdata = (byte[]) getPrivateBaseClassField("externalRSAdata");
		digestEncryptionAlgorithm = (String) getPrivateBaseClassField("digestEncryptionAlgorithm");
		range = (long[]) getPrivateBaseClassField("range");
		tempFile = (File) getPrivateBaseClassField("tempFile");
		bout = (byte[]) getPrivateBaseClassField("bout");
		raf = (RandomAccessFile) getPrivateBaseClassField("raf");
		stamper = (PdfStamper)getPrivateBaseClassField("stamper");
		cryptoDictionary = (PdfDictionary)getPrivateBaseClassField("cryptoDictionary");
		signatureEvent = (SignatureEvent) getPrivateBaseClassField("signatureEvent");
		
        if (preClosed) {
        	throw new DocumentException("Document already pre closed.");
        }
        
        preClosed = true;
        //preClosed in der Basisklasse setzen:
        setPreClosedinBaseClass();
		
        AcroFields af = writer.getAcroFields();
        String name = getFieldName();
        boolean fieldExists = !(isInvisible() || isNewField());
        PdfIndirectReference refSig = writer.getPdfIndirectReference();
        writer.setSigFlags(3);
        if (fieldExists) {
            PdfDictionary widget = af.getFieldItem(name).getWidget(0);
            
            //********** TB /V must go into fielditem rather than the widget dict! (IS-2297 Hinweis von CIB) *********
            Item fieldItem = af.getFieldItem(name);
            fieldItem.writeToAll(PdfName.V, refSig, AcroFields.Item.WRITE_VALUE|AcroFields.Item.WRITE_MERGED);
            fieldItem.markUsed(af, AcroFields.Item.WRITE_VALUE);
            //**************** ENDE FIX IS-2297 **********************************************************************
            
            writer.markUsed(widget);
            widget.put(PdfName.P, writer.getPageReference(getPage()));
            
            //********** TB /V must go into fielditem rather than the widget dict! (IS-2297 Hinweis von CIB) *********
            //Not into widget dict - that is wrong.
            //widget.put(PdfName.V, refSig);
            //**************** ENDE FIX IS-2297 **********************************************************************

            PdfObject obj = PdfReader.getPdfObjectRelease(widget.get(PdfName.F));
            int flags = 0;
            if (obj != null && obj.isNumber()) {
				flags = ((PdfNumber)obj).intValue();
			}
            flags |= PdfAnnotation.FLAGS_LOCKED;
            widget.put(PdfName.F, new PdfNumber(flags));
            PdfDictionary ap = new PdfDictionary();
            ap.put(PdfName.N, getAppearance().getIndirectReference());
            widget.put(PdfName.AP, ap);
        }
        else {
            PdfFormField sigField = PdfFormField.createSignature(writer);
            sigField.setFieldName(name);
            sigField.put(PdfName.V, refSig);
            sigField.setFlags(PdfAnnotation.FLAGS_PRINT | PdfAnnotation.FLAGS_LOCKED);

            int pagen = getPage();
            if (!isInvisible()) {
				sigField.setWidget(getPageRect(), null);
			}
			else {
				sigField.setWidget(new Rectangle(0, 0), null);
			}
            sigField.setAppearance(PdfAnnotation.APPEARANCE_NORMAL, getAppearance());
            sigField.setPage(pagen);
            writer.addAnnotation(sigField, pagen);
        }

        exclusionLocations = new HashMap();
        InsignPdfSigGenericPKCS sigStandard = (InsignPdfSigGenericPKCS) getPrivateBaseClassField("sigStandard");
        if (cryptoDictionary == null) {
        	
        	//RW: Do we need this?
        	
//            if (PdfName.ADOBE_PPKLITE.equals(getFilter())) {
//				sigStandard = new InsignPdfSigGenericPKCS.PPKLite(getProvider());
//			}
//			else if (PdfName.ADOBE_PPKMS.equals(getFilter())) {
//				sigStandard = new InsignPdfSigGenericPKCS.PPKMS(getProvider());
//			}
//			else if (PdfName.VERISIGN_PPKVS.equals(getFilter())) {
//				sigStandard = new InsignPdfSigGenericPKCS.VeriSign(getProvider());
//			}
//			else {
//				throw new IllegalArgumentException("Unknown filter: " + getFilter());
//			}
        	
        	String hashAlgo = InsignPdfPKCS7.getHashAlgoFromCert((X509Certificate)certKeyData.getChain()[0]);
            sigStandard = new InsignPdfSigGenericPKCS.INSIGN(getProvider(), hashAlgo);
            sigStandard.setExternalDigest(externalDigest, externalRSAdata, digestEncryptionAlgorithm);
            if (getReason() != null) {
				sigStandard.setReason(getReason());
			}
            if (getLocation() != null) {
				sigStandard.setLocation(getLocation());
			}
            if (getContact() != null) {
				sigStandard.setContact(getContact());
			}
            sigStandard.put(PdfName.M, new PdfDate(getSignDate()));
            sigStandard.setSignInfo(certKeyData.getKey(), certKeyData.getChain(), getCrlList());
            PdfString contents = (PdfString)sigStandard.get(PdfName.CONTENTS);
            PdfLiteral lit = new PdfLiteral((contents.toString().length() + (PdfName.ADOBE_PPKLITE.equals(getFilter())?0:64)) * 2 + 2);
            exclusionLocations.put(PdfName.CONTENTS, lit);
            sigStandard.put(PdfName.CONTENTS, lit);
            lit = new PdfLiteral(80);
            exclusionLocations.put(PdfName.BYTERANGE, lit);
            sigStandard.put(PdfName.BYTERANGE, lit);
            if (getCertificationLevel() > 0) {
                addDocMDP(sigStandard);
            }
            if (signatureEvent != null) {
				signatureEvent.getSignatureDictionary(sigStandard);
			}
            writer.addToBody(sigStandard, refSig, false);
            this.sigStandard = sigStandard;
            
        }
        else {
            PdfLiteral lit = new PdfLiteral(80);
            exclusionLocations.put(PdfName.BYTERANGE, lit);
            cryptoDictionary.put(PdfName.BYTERANGE, lit);
            for (Iterator it = exclusionSizes.entrySet().iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry)it.next();
                PdfName key = (PdfName)entry.getKey();
                Integer v = (Integer)entry.getValue();
                lit = new PdfLiteral(v.intValue());
                exclusionLocations.put(key, lit);
                cryptoDictionary.put(key, lit);
            }
            if (getCertificationLevel() > 0) {
				addDocMDP(cryptoDictionary);
			}
            if (signatureEvent != null) {
				signatureEvent.getSignatureDictionary(cryptoDictionary);
			}
            writer.addToBody(cryptoDictionary, refSig, false);
        }
        if (getCertificationLevel() > 0) {
          // add DocMDP entry to root
             PdfDictionary docmdp = new PdfDictionary();
             docmdp.put(new PdfName("DocMDP"), refSig);
             writer.reader.getCatalog().put(new PdfName("Perms"), docmdp);
        }
        writer.close(stamper.getMoreInfo());
        
        range = new long[exclusionLocations.size() * 2];
        long byteRangePosition = ((PdfLiteral)exclusionLocations.get(PdfName.BYTERANGE)).getPosition();
        exclusionLocations.remove(PdfName.BYTERANGE);
        int idx = 1;
        for (Iterator it = exclusionLocations.values().iterator(); it.hasNext();) {
            PdfLiteral lit = (PdfLiteral)it.next();
            long n = lit.getPosition();
            range[idx++] = n;
            range[idx++] = lit.getPosLength() + n;
        }
        Arrays.sort(range, 1, range.length - 1);
        for (int k = 3; k < range.length - 2; k += 2) {
			range[k] -= range[k - 1];
		}
        
        if (tempFile == null) {
            bout = getSigout().buf;
            boutLen = getSigout().size();
            range[range.length - 1] = boutLen - range[range.length - 2];
            @Cleanup
            ByteBuffer bf = new ByteBuffer();
            bf.append('[');
            for (long element : range) {
				bf.append(element).append(' ');
			}
            bf.append(']');
            System.arraycopy(bf.getBuffer(), 0, bout,(int) byteRangePosition, bf.size());
            closeQuietly(bf);
        }
        else {
            try {
                raf = new RandomAccessFile(tempFile, "rw");
                int boutLen = (int)raf.length();
                range[range.length - 1] = boutLen - range[range.length - 2];
                @Cleanup
                ByteBuffer bf = new ByteBuffer();
                bf.append('[');
                for (long element : range) {
					bf.append(element).append(' ');
				}
                bf.append(']');
                raf.seek(byteRangePosition);
                raf.write(bf.getBuffer(), 0, bf.size());
                closeQuietly(bf);
            }
            catch (IOException e) {
                try{raf.close();}catch(Exception ee){}
                try{tempFile.delete();}catch(Exception ee){}
                throw e;
            }
        }
        //set range in Baseclass
        setObjectInBaseClass(range, "range");
        //set cryptoDictionary in Baseclass
        setObjectInBaseClass(cryptoDictionary, "cryptoDictionary");
        //set raf in Baseclass
        setObjectInBaseClass(raf, "raf");
        //set exclusionLocations in Baseclass
        setObjectInBaseClass(exclusionLocations, "exclusionLocations");
        //set bout in BaseClass
        setObjectInBaseClass(bout, "bout");
        //set boutLen in BaseClass
        
        Field oFieldContext = null;
		try {
			oFieldContext = PdfSignatureAppearance.class.getDeclaredField("boutLen");
			oFieldContext.setAccessible(true);
			oFieldContext.setInt(this, boutLen);
		} catch (Exception e){
			throw new RuntimeException("Error@Reflection", e);
		}
		
      
    }
	

	/**
     * Adds keys to the signature dictionary that define
     * the certification level and the permissions.
     * This method is only used for Certifying signatures.
     * @param crypto the signature dictionary
     */
	
    private void addDocMDP(PdfDictionary crypto) {
    	PdfDictionary reference = new PdfDictionary();
        PdfDictionary transformParams = new PdfDictionary();
        transformParams.put(PdfName.P, new PdfNumber(getCertificationLevel()));
        transformParams.put(PdfName.V, new PdfName("1.2"));
        transformParams.put(PdfName.TYPE, PdfName.TRANSFORMPARAMS);
        reference.put(PdfName.TRANSFORMMETHOD, PdfName.DOCMDP);
        reference.put(PdfName.TYPE, PdfName.SIGREF);
        reference.put(PdfName.TRANSFORMPARAMS, transformParams);
        
        /*
    	 * 
    	 * http://gitlab.itextsupport.com/itext/itextpdf/commit/1b8e869e1ba8569520d1783e21428d7c33c4e4ec
    	 * CUSTOM starts HERE
    	 */
        if(stamper != null  && stamper.getReader() != null) {
        	
        	if (stamper.getReader().getPdfVersion() < PdfWriter.VERSION_1_6 && !isPDFA_U()) {
                logger.log(Level.INFO,"PDF-version below 1.6 and no PDF/A-3u. Using digest params!");
                reference.put(new PdfName("DigestValue"), new PdfString("aa"));
                PdfArray loc = new PdfArray();
                loc.add(new PdfNumber(0));
                loc.add(new PdfNumber(0));
                reference.put(new PdfName("DigestLocation"), loc);
                reference.put(new PdfName("DigestMethod"), new PdfName("MD5"));
            }else {
                logger.log(Level.INFO,"PDF-version above 1.6. Not using digest params.");
            }
        }else {
            logger.log(Level.INFO,"Internal error: PDF-version cannot be determined. reader or stamper is not present.");
        }
        /*
    	 * CUSTOM ends HERE
    	 */
        
        
        reference.put(PdfName.DATA, writer.reader.getTrailer().get(PdfName.ROOT));
        PdfArray types = new PdfArray();
        types.add(reference);
        crypto.put(PdfName.REFERENCE, types);
    }

    public static String[] encodings=new String[] {"UTF-8","UTF-16","UTF-32","ISO-8859-1","US-ASCII"};

	/**
	 * Nürnberger: Standard ist hier offiziell "PDF/A-3u", Writer < 1.6 (1.4) damit ist alles mit "Digest*" invalide
	 * @return
	 */
	private boolean isPDFA_U() {
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			byte[] b = stamper.getReader().getMetadata();
			if (b == null) {
				return false;
			}
            Node node=null;
            for (String encoding: encodings) {
                try {
                    InputSource inputSource = new InputSource(new InputStreamReader(new ByteArrayInputStream(b), encoding));
                    inputSource.setEncoding(encoding);
                    node = (Node) xpath.evaluate("//*[local-name()='conformance']", inputSource, XPathConstants.NODESET);
                    break;
                } catch (Exception e) {
                    //ignore an try next
                }
            }
            if (node==null) {
                logger.log(Level.WARNING,"Reading PDF metadata finally failed.");
                return false;
            }
			return node != null && node.getTextContent().equalsIgnoreCase("U");
		} catch (Exception e) {
            logger.log(Level.SEVERE,"isPDFA_U",e);
			return false;
		}
	}
    
    private void closeQuietly(OutputStream stream) {
    	try {
    		stream.close();
    	}
    	catch (IOException ex) {
            logger.log(Level.SEVERE,"close",ex);
    	}
    }
    
    public void setKeyData(KeyData certKeyData) {
    	this.certKeyData = certKeyData;
    }
    
    
    public InsignPdfSigGenericPKCS getInsignSigStandard() {
    	return sigStandard;
    }

	
}
