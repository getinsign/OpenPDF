package com.lowagie.text.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.error_messages.MessageLocalization;
import com.lowagie.text.pdf.AcroFields.Item;

import de.is2.sign.utils.PdfHelper;

public class InsignPdfStamperImp extends PdfStamperImp  {
    private static Logger logger = Logger.getLogger(InsignPdfStamperImp.class.getName());
    private String appVersion;

    public InsignPdfStamperImp(final PdfReader reader, final OutputStream os, final char pdfVersion, final boolean append, String appVersion)
			throws DocumentException, IOException {
		super(reader, os, pdfVersion, append);
        this.appVersion = appVersion;
    }

    public static Object getType(Object obj, Class<?> type) {
        if (obj != null && type != null && type.isInstance(obj)) {
            return obj;
        }
        return null;
    }

	/**
	 * Customizing: Nullcheck and structure changes to keep pdf/a status
	 * 
	 * @return void
	 * @author RolandW
	 * @since 18.04.2013
	 */
	@Override
	protected void flatFields() {
		if (append)
            throw new IllegalArgumentException(MessageLocalization.getComposedMessage("field.flattening.is.not.supported.in.append.mode"));
        getAcroFields();
        Map<String, Item> fields = acroFields.getAllFields();
        HashSet<Integer> flattenedReferenceNumbers = new HashSet<Integer>();
        if (fieldsAdded && partialFlattening.isEmpty()) {
            partialFlattening.addAll(fields.keySet());
        }
        PdfDictionary acroForm = reader.getCatalog().getAsDict(PdfName.ACROFORM);
        PdfArray acroFds = null;
        if (acroForm != null) {
            acroFds = (PdfArray)PdfReader.getPdfObject(acroForm.get(PdfName.FIELDS), acroForm);
        }
        for (Map.Entry<String, Item> entry : fields.entrySet()) {
            String name = entry.getKey();
            if (!partialFlattening.isEmpty() && !partialFlattening.contains(name))
                continue;
            Item item = entry.getValue();
            for (int k = 0; k < item.size(); ++k) {
                PdfDictionary merged = item.getMerged(k);
                
                PdfNumber ff = merged.getAsNumber(PdfName.F);
                int flags = 0;
                if (ff != null)
                    flags = ff.intValue();
                int page = item.getPage(k);
                if (page == -1)
                	continue;
                PdfDictionary appDic = merged.getAsDict(PdfName.AP);
                if (appDic != null && (flags & PdfFormField.FLAGS_PRINT) != 0 && (flags & PdfFormField.FLAGS_HIDDEN) == 0) {
                    PdfObject obj = appDic.get(PdfName.N);
                    PdfAppearance app = null;
                    if (obj != null) {
                        PdfObject objReal = PdfReader.getPdfObject(obj);
                        if (obj instanceof PdfIndirectReference && !obj.isIndirect())
                            app = new PdfAppearance((PdfIndirectReference) obj);
                        else if (objReal instanceof PdfStream) {
                            ((PdfDictionary) objReal).put(PdfName.SUBTYPE, PdfName.FORM);
                            app = new PdfAppearance((PdfIndirectReference) obj);
                        } else {
                            if (objReal != null && objReal.isDictionary()) {
                                PdfName as = merged.getAsName(PdfName.AS);
                                if (as != null) {
                                    PdfIndirectReference iref = (PdfIndirectReference) ((PdfDictionary) objReal).get(as);
                                    if (iref != null) {
                                        app = new PdfAppearance(iref);
                                        if (iref.isIndirect()) {
                                            objReal = PdfReader.getPdfObject(iref);
                                            ((PdfDictionary) objReal).put(PdfName.SUBTYPE, PdfName.FORM);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (app != null) {
                        Rectangle box = PdfReader.getNormalizedRectangle(merged.getAsArray(PdfName.RECT));
                        PdfContentByte cb = getOverContent(page);
                        cb.setLiteral("Q ");
                        cb.addTemplate(app, box.getLeft(), box.getBottom());
                        cb.setLiteral("q ");
                    }
                }
                if (partialFlattening.isEmpty())
                    continue;
                PdfDictionary pageDic = reader.getPageN(page);
                PdfArray annots = pageDic.getAsArray(PdfName.ANNOTS);
                if (annots == null)
                    continue;
                
                if (item.getWidgetRef(k) != null) {
                	flattenedReferenceNumbers.add(item.getWidgetRef(k).getNumber());
                }
                
                for (int idx = 0; idx < annots.size(); ++idx) {
                    PdfObject ran = annots.getPdfObject(idx);
                    if (!ran.isIndirect())
                        continue;
                    PdfObject ran2 = item.getWidgetRef(k);
                    if (!ran2.isIndirect())
                        continue;
                    if (((PRIndirectReference) ran).getNumber() == ((PRIndirectReference) ran2).getNumber()) {
                        annots.remove(idx--);
                        PRIndirectReference wdref = (PRIndirectReference) ran2;
                        while (true) {
                            PdfDictionary wd = (PdfDictionary) PdfReader.getPdfObject(wdref);
                          //RW:Customizing: Nullcheck
                            final PRIndirectReference parentRef;
                            if(wd != null){
                            	parentRef = (PRIndirectReference)wd.get(PdfName.PARENT);
                            }
                            else{
                            	parentRef = null;
                            }
                            PdfReader.killIndirect(wdref);
                            if (parentRef == null && acroFds != null) { // reached AcroForm
                                for (int fr = 0; fr < acroFds.size(); ++fr) {
                                    PdfObject h = acroFds.getPdfObject(fr);
                                    if (h.isIndirect() && ((PRIndirectReference) h).getNumber() == wdref.getNumber()) {
                                        acroFds.remove(fr);
                                        --fr;
                                    }
                                }
                                break;
                            }
                            PdfDictionary parent = (PdfDictionary) PdfReader.getPdfObject(parentRef);
                            if (parent != null) {
                            	PdfArray kids = parent.getAsArray(PdfName.KIDS);
                            	if (kids != null) {
                            		for (int fr = 0; fr < kids.size(); ++fr) {
                                        PdfObject h = kids.getPdfObject(fr);
                                        if (h.isIndirect() && ((PRIndirectReference) h).getNumber() == wdref.getNumber()) {
                                            kids.remove(fr);
                                            --fr;
                                        }
                                    }
                                    if (!kids.isEmpty())
                                        break;
                                    wdref = parentRef;
                            	}
                            }
                            
                        }
                    }
                }
                if (annots.isEmpty()) {
                    PdfReader.killIndirect(pageDic.get(PdfName.ANNOTS));
                    pageDic.remove(PdfName.ANNOTS);
                }
            }
        }
        if (!fieldsAdded && partialFlattening.isEmpty()) {
            for (int page = 1; page <= reader.getNumberOfPages(); ++page) {
                PdfDictionary pageDic = reader.getPageN(page);
                PdfArray annots = pageDic.getAsArray(PdfName.ANNOTS);
                if (annots == null)
                    continue;
                for (int idx = 0; idx < annots.size(); ++idx) {
                    PdfObject annoto = annots.getDirectObject(idx);
                    if ((annoto instanceof PdfIndirectReference) && !annoto.isIndirect())
                        continue;
                    if (!annoto.isDictionary() || PdfName.WIDGET.equals(((PdfDictionary)annoto).get(PdfName.SUBTYPE))) {
                        annots.remove(idx);
                        --idx;
                    }
                }
                if (annots.isEmpty()) {
                    PdfReader.killIndirect(pageDic.get(PdfName.ANNOTS));
                    pageDic.remove(PdfName.ANNOTS);
                }
            }
            eliminateAcroformObjects();
        }
        
        //Custom
       try {
    	   PdfDictionary parentTree = (PdfDictionary) PdfReader.getPdfObject(
    			   PdfHelper.getValueFromTree(reader.getCatalog(), new String[] { "StructTreeRoot", "ParentTree"}));
           if (parentTree != null) {
        	   PdfArray nums = parentTree.getAsArray(new PdfName("Nums"));
        	   for (PdfObject obj: nums.arrayList) {
        		   PdfDictionary dic = null;
        		   if (obj instanceof PdfDictionary) {
        			   dic = (PdfDictionary) obj;
        		   }
        		   
        		   if (obj instanceof PRIndirectReference) {
        			   PdfObject direct = PdfReader.getPdfObject(obj);
        			   if (direct instanceof PdfDictionary) {
            			   dic = (PdfDictionary) direct;
            		   }
        		   }
        		   if (dic != null) {
        			   PdfArray array = dic.getAsArray(new PdfName("K"));
        			   
        			   if (array != null) {
        				   ArrayList<Integer> keepIndexes = new ArrayList<Integer>();
            			   
            			   PdfArray newEntry = new PdfArray();
            			   for (int i = 0; i < array.size(); i++) {
            				   PdfDictionary innerDic = array.getAsDict(i);
            				   newEntry.add(innerDic);
            				   keepIndexes.add(i);
            				   if (innerDic != null) {
            					   PdfName type = innerDic.getAsName(new PdfName("Type"));
            					   PdfIndirectReference link = innerDic.getAsIndirectObject(new PdfName("Obj"));
            					   
            					   if (type != null && "/OBJR".equalsIgnoreCase(type.toString()) && 
            							   (link == null || flattenedReferenceNumbers.contains(link.getNumber()))) {
            						   newEntry.remove(newEntry.size() - 1);
            					   }
            				   }
            			   }
            			   dic.put(new PdfName("K"), newEntry);
        			   }
        			   
        		   }
        	   }
           }
       } catch (Exception e) {
    	 //Protection against invalid pdfs regarding the structure, we don't want to end stamping here. 
       }
       
    }
	
	@Override
	protected AcroFields getAcroFields() {
        if (acroFields == null) {
            acroFields = new InsignAcroFields(reader, this);
        }
        return acroFields;
    }
	
	@Override
	void close(Map<String, String> moreInfo) throws IOException {
		if (closed) {
			return;
		}
		fixProducerEncoding();
		super.close(moreInfo);
	}

	//IS-836: Fix iText Decoding UTF-8 producer String
	protected void fixProducerEncoding() {
		PRIndirectReference iInfo = (PRIndirectReference) reader.getTrailer().get(PdfName.INFO);
		PdfDictionary oldInfo = (PdfDictionary) PdfReader.getPdfObject(iInfo);
		PdfString producer = null;
		if (oldInfo!=null)
		{
			if (oldInfo.get(PdfName.PRODUCER) != null) {
				producer = oldInfo.getAsString(PdfName.PRODUCER);
			}
			String prodstr="iS2 inSign";
			if (producer!=null)
			{
				if (producer.toString().length()>2 && producer.toString().charAt(0)==(char)254  && producer.toString().charAt(1)==(char)255 )
				{
					//UTF-16 Producer Value will lead to iText Encoding error
					try {
						prodstr = new String(producer.getBytes(), "UTF-16BE");
					}
					catch (UnsupportedEncodingException e) {
                        logger.log(Level.WARNING,"Error decoding pdf producer",e);
					}
				} else
				{
					prodstr = producer.toString();
				}
				if (prodstr.contains("inSign")==false) {
					prodstr += "; modified using iS2 inSign " + appVersion;
				}
				oldInfo.put(PdfName.PRODUCER, new PdfString(prodstr));
			}
		}
	}
}
