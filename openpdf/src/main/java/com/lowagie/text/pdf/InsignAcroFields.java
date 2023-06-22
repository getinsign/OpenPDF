package com.lowagie.text.pdf;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Node;

import com.lowagie.text.DocumentException;
import com.lowagie.text.ExceptionConverter;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.error_messages.MessageLocalization;

/**
 * @see com.lowagie.text.pdf.AcroFields
 *
 * Änderung hier nötig um die Reihenfolge der Felder aus dem PDF zu erhalten, da diese für BIPRO Dokumente von Bedeutung ist.
 * iText wirft diese durch verwendung von Hashmaps etc. immer wieder drucheinander.
 */

public class InsignAcroFields extends AcroFields
{

	private static Logger logger = Logger.getLogger(InsignAcroFields.class.getName());
	/**
	 * Fieldstates mit Typ des States
	 * 
	 * @author RolandW
	 * @since 22.04.2013
	 */
	public class States{
		
		boolean realGroup;//RW: Teil einer PseudoGruppe (Duplikate?) oder nicht (Radionbuttons)
		List<String> entries;//Set wär zwar schöner, aber mit List ist es kompatibler
		
		public States(final List<String> entries, final boolean realGroup){
			this.entries = entries;
			this.realGroup = realGroup;
		}

		public boolean isRealGroup() {
			return realGroup;
		}

		public List<String> getEntries() {
			return entries;
		}
		
	}

	private final boolean append;
	
	public InsignAcroFields(final PdfReader paramPdfReader, final PdfWriter paramPdfWriter) {
		super(paramPdfReader, paramPdfWriter);
		if (writer instanceof PdfStamperImp) {
			append = ((PdfStamperImp) writer).isAppend();
		}
		else{
			append = false;
		}
	}

	public InsignAcroFields(final PdfReader reader) {
		super(reader, null);
		if (writer instanceof PdfStamperImp) {
			append = ((PdfStamperImp) writer).isAppend();
		}
		else{
			append = false;
		}
	}
	
	@Override
	void fill() {
		initializeField();
		
	    PdfDictionary top = (PdfDictionary) PdfReader.getPdfObjectRelease(reader.getCatalog().get(PdfName.ACROFORM));
	    if (top == null) {
	      return;
	    }
	    PdfArray arrfds = (PdfArray) PdfReader.getPdfObjectRelease(top.get(PdfName.FIELDS));
	    if (arrfds == null) {// || arrfds.size() == 0) { //Fixed here, see PDF in ISS-1965
	      return;
	    }
	    for (int k = 1; k <= reader.getNumberOfPages(); ++k) {
	      PdfDictionary page = reader.getPageNRelease(k);
	      PdfObject pdfObjectRelease = PdfReader.getPdfObjectRelease(page.get(PdfName.ANNOTS), page);
	      if (pdfObjectRelease == null || !(pdfObjectRelease instanceof PdfArray)) {
	    	  continue;
	      }
	      PdfArray annots = (PdfArray) pdfObjectRelease;
	      for (int j = 0; j < annots.size(); ++j) {
	        PdfDictionary annot = annots.getAsDict(j);
	        if (annot == null) {
	          PdfReader.releaseLastXrefPartial(annots.getAsIndirectObject(j));
	          continue;
	        }
	        if (!PdfName.WIDGET.equals(annot.getAsName(PdfName.SUBTYPE))) {
	          PdfReader.releaseLastXrefPartial(annots.getAsIndirectObject(j));
	          continue;
	        }
	        PdfDictionary widget = annot;
	        PdfDictionary dic = new PdfDictionary();
	        dic.putAll(annot);
	        String name = "";
	        PdfDictionary value = null;
	        PdfObject lastV = null;
	        while (annot != null) {
	          dic.mergeDifferent(annot);
	          PdfString t = annot.getAsString(PdfName.T);
	          if (t != null) {
	            name = t.toUnicodeString() + "." + name;
	          }
	          if (lastV == null && annot.get(PdfName.V) != null) {
	            lastV = PdfReader.getPdfObjectRelease(annot.get(PdfName.V));
	          }
	          if (value == null && t != null) {
	            value = annot;
	            if (annot.get(PdfName.V) == null && lastV != null) {
	              value.put(PdfName.V, lastV);
	            }
	          }
	          annot = annot.getAsDict(PdfName.PARENT);
	        }
	        if (name.length() > 0) {
	          name = name.substring(0, name.length() - 1);
	        }
	        Item item = (Item) getFields().get(name);
	        if (item == null) {
	          item = new Item();
	          getFields().put(name, item);
	        }
	        if (value == null) {
	          item.addValue(widget);
	        } else {
	          item.addValue(value);
	        }
	        item.addWidget(widget);
	        item.addWidgetRef(annots.getAsIndirectObject(j)); // must be a reference
	        if (top != null) {
	          dic.mergeDifferent(top);
	        }
	        item.addMerged(dic);
	        item.addPage(k);
	        item.addTabOrder(j);
	      }
	    }
	    // some tools produce invisible signatures without an entry in the page annotation array
	    // look for a single level annotation
	    PdfNumber sigFlags = top.getAsNumber(PdfName.SIGFLAGS);
	    if (sigFlags == null || (sigFlags.intValue() & 1) != 1) {
	      return;
	    }
	    for (int j = 0; j < arrfds.size(); ++j) {
	      PdfDictionary annot = arrfds.getAsDict(j);
	      if (annot == null) {
	        PdfReader.releaseLastXrefPartial(arrfds.getAsIndirectObject(j));
	        continue;
	      }
	      if (!PdfName.WIDGET.equals(annot.getAsName(PdfName.SUBTYPE))) {
	        PdfReader.releaseLastXrefPartial(arrfds.getAsIndirectObject(j));
	        continue;
	      }
	      PdfArray kids = (PdfArray) PdfReader.getPdfObjectRelease(annot.get(PdfName.KIDS));
	      if (kids != null) {
	        continue;
	      }
	      PdfDictionary dic = new PdfDictionary();
	      dic.putAll(annot);
	      PdfString t = annot.getAsString(PdfName.T);
	      if (t == null) {
	        continue;
	      }
	      String name = t.toUnicodeString();
	      if (getFields().containsKey(name)) {
	        continue;
	      }
	      Item item = new Item();
	      getFields().put(name, item);
	      item.addValue(dic);
	      item.addWidget(dic);
	      item.addWidgetRef(arrfds.getAsIndirectObject(j)); // must be a reference
	      item.addMerged(dic);
	      item.addPage(-1);
	      item.addTabOrder(-1);
	    }
	  }

	private void initializeField() {
		try {
			Field declaredField = AcroFields.class.getDeclaredField("fields");
			declaredField.setAccessible(true);
			declaredField.set(this, new HashMap());
		}
		catch (Exception e) {
			logger.log(Level.SEVERE, "initializeField",e);
		}
	}


	
	/**
	 * 
	 * Copypasta, wg. Ordered Map und Param für Fielditem
	 * Update: Weiterhin jetzt per Fielditem + Rückgabetyp angepasst
	 */
	public States getAppearanceStates(final String fieldName, final int index) {
		boolean isGroup = false;
        final Item fd = (Item)getFields().get(fieldName);
        if (fd == null) {
			return null;
		}
        final Set<String> names = new LinkedHashSet<String>();
        PdfDictionary dic = fd.getWidget( index );
        if ((dic != null) &&
        		((dic = dic.getAsDict(PdfName.AP)) != null) &&
        		((dic = dic.getAsDict(PdfName.N)) != null)) {
        	
        	 for (final Iterator<?> it = dic.getKeys().iterator(); it.hasNext();) {
                 final String name = PdfName.decodeName(((PdfName)it.next()).toString());
                 names.add(name);
             }
		}
        return new States(new ArrayList<String>(names), isGroup);
    }
	

    /**
     * Einzelne Position für Item holen
     * @return float[]
     */
    public float[] getFieldPosition(final Item item, final int index) {
     
    final float ret[] = new float[5];
    int ptr = 0;
        try {
            final PdfDictionary wd = item.getWidget(index);
            final PdfArray rect = wd.getAsArray(PdfName.RECT);
            if (rect == null) {
            	throw new PdfException("No rect");
			}
            Rectangle r = PdfReader.getNormalizedRectangle(rect);
            int page = item.getPage(index).intValue();
         
            if (page == -1) {
            	throw new PdfException("No page for annotation");
            	
            }
            int rotation = 0;
            rotation = reader.getPageRotation(page);
           
            ret[ptr++] = page;
            if (rotation != 0) {
                final Rectangle pageSize = reader.getCropBox(page);//reader.getPageSize(page);
                switch (rotation) {
                    case 270:
                        r = new Rectangle(
                            pageSize.getTop() - r.getBottom(),
                            r.getLeft(),
                            pageSize.getTop() - r.getTop(),
                            r.getRight());
                        break;
                    case 180:
                        r = new Rectangle(
                            pageSize.getRight() - r.getLeft(),
                            pageSize.getTop() - r.getBottom(),
                            pageSize.getRight() - r.getRight(),
                            pageSize.getTop() - r.getTop());
                        break;
                    case 90:
                        r = new Rectangle(
                            r.getBottom(),
                            pageSize.getRight() - r.getLeft(),
                            r.getTop(),
                            pageSize.getRight() - r.getRight());
                        break;
                }
                r.normalize();
            }
            ret[ptr++] = r.getLeft();
            ret[ptr++] = r.getBottom();
            ret[ptr++] = r.getRight();
            ret[ptr++] = r.getTop();
        }
        catch (final Exception e) {
        	logger.log(Level.SEVERE,"getFieldPosition",e);
            // empty on purpose
        }
        if (ptr < ret.length) {
            final float ret2[] = new float[ptr];
            System.arraycopy(ret, 0, ret2, 0, ptr);
            return ret2;
        }
        return ret;
    }


    /**
     * Dies Methode wird benutzt, um Formularfelder direkt in die innere AcroFields und sonstige Strukturen einzufügen
     * um diese dann in einem Schritt auch flatten zu können. Dies geht normalerwiese sonst nicht.
     * @param taborder
     */
    @SuppressWarnings("unchecked")
	public void addField(final PdfStamper stamper, final PdfFormField formFieldDict, final int pageNum, final Object fldName, final int taborder){
    	
    	/*The underlying problem, as I understand it:
    	1) an AcroFields object is created when the PDF is opened.
    	2) newly created fields are not added to the existing AcroFields.
    	3) Flattening works on the information in the AcroFields.
    	All AcroFields.Item's modifier functions are package private, and there's no way to add an Item from outside the class an....
    	Oh Yes There Is.
    	AcroFields.getFields just hands over its internal name->Item map, so if you could construct the proper Item object, you could trivially insert it.
    	Potential solutions (if I'm right):
    	*/
    			
    	
    	final PdfReader reader = stamper.getReader();
    	// you'll have to manage that one on your own
    	final PRIndirectReference fieldRef = reader.addPdfObject(formFieldDict);
    	// add it to the page
    	final PdfDictionary pageDict = reader.getPageN(pageNum);
    	PdfArray annots = pageDict.getAsArray(PdfName.ANNOTS);
    	if (annots == null) {
    		annots = new PdfArray();
    		pageDict.put(PdfName.ANNOTS, annots);
    	}
    	annots.add(fieldRef);
    	// add it to the doc-level field list
    	final PdfDictionary root = reader.getCatalog();
    	PdfDictionary acroFrm = root.getAsDict(PdfName.ACROFORM);
    	if (acroFrm==null)
    	{
    		acroFrm=new PdfDictionary();
    		root.put(PdfName.ACROFORM, acroFrm);
    	}
    	PdfArray flds = acroFrm.getAsArray(PdfName.FIELDS);
    	if (flds==null)
    	{
    		flds=new PdfArray();
    		acroFrm.put(PdfName.FIELDS, flds);
    	}
    	flds.add(fieldRef);
    	// just tack it on the end.
    	//PdfStamper stamper = new PdfStamper(reader, outStream);
    	//This method adds the field to the reader prior to the creation of the AcroFields object, so that when
    	//it works its way through the PDF, it finds your new field.
    	//2) Modify your copy of the iText source to make the various AcroFields.Item.add* function public and use
    	//them to create a new AcroFields.Item and add it to the AcroFields' field map.
    	final AcroFields.Item item = new AcroFields.Item();
    	item.addPage( pageNum );
    	item.addWidget( formFieldDict );
    	item.addWidgetRef( fieldRef );
    	item.addValue( formFieldDict );
    	item.addMerged( formFieldDict );
    	item.addTabOrder(taborder);
    	//item.addTabOrder( fldDictsIndexInAnnotsArray );
    	getFields().put(fldName, item);
    	//--Mark Storer   Senior Software Engineer   Cardiff.com   import legalese.Disclaimer; Disclaimer<Cardiff> DisCard = null;
    }
    
    /**
	 * 
	 * Copypasta, wg. fehlendem Null-Check s.u.
	 *
     * @override @see com.lowagie.text.pdf.AcroFields#removeField(java.lang.String, int)
     * @author RolandW
     * @since 15.01.2014
     */
    @Override
	public boolean removeField(final String name, final int page) {
        final Item item = getFieldItem(name);
        if (item == null) {
			return false;
		}
        final PdfDictionary acroForm = (PdfDictionary)PdfReader.getPdfObject(reader.getCatalog().get(PdfName.ACROFORM), reader.getCatalog());

        if (acroForm == null) {
			return false;
		}
        final PdfArray arrayf = acroForm.getAsArray(PdfName.FIELDS);
        if (arrayf == null) {
			return false;
		}
        for (int k = 0; k < item.size(); ++k) {
            final int pageV = item.getPage(k).intValue();
            if (page != -1 && page != pageV) {
				continue;
			}
            PdfIndirectReference ref = item.getWidgetRef(k);
            PdfDictionary wd = item.getWidget( k );
            final PdfDictionary pageDic = reader.getPageN(pageV);
            if(pageDic == null){ //RW: Missing Check -----------------------------------------------
            	continue;
            }
            final PdfArray annots = pageDic.getAsArray(PdfName.ANNOTS);
            if (annots != null) {
                if (removeRefFromArray(annots, ref) == 0) {
                    pageDic.remove(PdfName.ANNOTS);
                    markUsed(pageDic);
                }
				else {
					markUsed(annots);
				}
            }
            PdfReader.killIndirect(ref);
            PdfIndirectReference kid = ref;
            if (wd!=null)
            {
	            while ((ref = wd.getAsIndirectObject(PdfName.PARENT)) != null) {
	                wd = wd.getAsDict( PdfName.PARENT );
	                if (wd!=null)
	                {
	                	final PdfArray kids = wd.getAsArray(PdfName.KIDS);
	                	if (removeRefFromArray(kids, kid) != 0) {
	                		break;
	                	}
	                }
	                else
	                {
	                	break;
	                }
	                kid = ref;
	                PdfReader.killIndirect(ref);
	            }
            }
            if (ref == null) {
                removeRefFromArray(arrayf, kid);
                markUsed(arrayf);
            }
            if (page != -1) {
                item.remove( k );
                --k;
            }
        }
        if (page == -1 || item.size() == 0) {
        	getFields().remove(name);
		}
        return true;
    }
    
    protected int removeRefFromArray(final PdfArray array, final PdfObject refo) {
    	if (array == null) {
        	return -1;
        }
        if (refo == null || !refo.isIndirect()) {
			return array.size();
		}
        
        final PdfIndirectReference ref = (PdfIndirectReference)refo;
        for (int j = 0; j < array.size(); ++j) {
            final PdfObject obj = array.getPdfObject(j);
            if (!obj.isIndirect()) {
				continue;
			}
            if (((PdfIndirectReference)obj).getNumber() == ref.getNumber()) {
				array.remove(j--);
			}
        }
        return array.size();
    }
    
    protected void markUsed(final PdfObject obj) {
        if (!append) {
			return;
		}
        ((PdfStamperImp)writer).markUsed(obj);
    }

    
    //Beim setzen von Radios die "Options-Liste" ignorieren, weil dies bei doppelten Einträgen nicht richtig funktioniert (Bsp. Fam VP2 bei Signal Iduna PDF)
    @Override
    public boolean setField(String name, String value, String display) throws IOException, DocumentException {
    	boolean ret = setFieldSuper(name, value, display);
    	Item item = (Item)getFields().get(name);
        if (item == null) {
			return false;
		}
        PdfDictionary merged = item.getMerged( 0 );
        PdfName type = merged.getAsName(PdfName.FT);
        if (PdfName.BTN.equals(type)) {
            PdfNumber ff = item.getMerged(0).getAsNumber(PdfName.FF);
            int flags = 0;
            if (ff != null) {
				flags = ff.intValue();
			}
            PdfName v = new PdfName(value);
            PdfName valt = null;
            PdfName vt = v;
            for (int idx = 0; idx < item.size(); ++idx) {
                merged = item.getMerged(idx);
                PdfDictionary widget = item.getWidget(idx);
                PdfDictionary valDict = item.getValue(idx);
                markUsed(item.getValue(idx));
                valDict.put(PdfName.V, v);
                merged.put(PdfName.V, v);
                markUsed(widget);
                if (isInAP(widget,  vt)) {
                    merged.put(PdfName.AS, vt);
                    widget.put(PdfName.AS, vt);
                }
                else {
                    merged.put(PdfName.AS, PdfName.Off);
                    widget.put(PdfName.AS, PdfName.Off);
                }
            }
        }
        return ret;
    }
    
    public boolean setFieldSuper(String name, String value, String display) throws DocumentException, IOException {
    	if (writer == null) {
  	      throw new DocumentException(MessageLocalization.getComposedMessage("this.acrofields.instance.is.read.only"));
  	    }
  	    if (getXfa().isXfaPresent()) {
  	      name = getXfa().findFieldName(name, this);
  	      if (name == null) {
  	        return false;
  	      }
  	      String shortName = XfaForm.Xml2Som.getShortName(name);
  	      Node xn = getXfa().findDatasetsNode(shortName);
  	      if (xn == null) {
  	        xn = getXfa().getDatasetsSom().insertNode(getXfa().getDatasetsNode(), shortName);
  	      }
  	      getXfa().setNodeText(xn, value);
  	    }
  	    Item item = (Item) getFields().get(name);
  	    if (item == null) {
  	      return false;
  	    }
  	    PdfDictionary merged = item.getMerged(0);
  	    PdfName type = merged.getAsName(PdfName.FT);
  	    if (PdfName.TX.equals(type)) {
  	      PdfNumber maxLen = merged.getAsNumber(PdfName.MAXLEN);
  	      int len = 0;
  	      if (maxLen != null) {
  	        len = maxLen.intValue();
  	      }
  	      if (len > 0) {
  	        value = value.substring(0, Math.min(len, value.length()));
  	      }
  	    }
  	    if (display == null) {
  	      display = value;
  	    }
  	    if (PdfName.TX.equals(type) || PdfName.CH.equals(type)) {
  	      PdfString v = new PdfString(value, PdfObject.TEXT_UNICODE);
  	      for (int idx = 0; idx < item.size(); ++idx) {
  	        PdfDictionary valueDic = item.getValue(idx);
  	        valueDic.put(PdfName.V, v);
  	        valueDic.remove(PdfName.I);
  	        markUsed(valueDic);
  	        merged = item.getMerged(idx);
  	        merged.remove(PdfName.I);
  	        merged.put(PdfName.V, v);
  	        PdfDictionary widget = item.getWidget(idx);
  	        if (isGenerateAppearances()) {
  	          PdfAppearance app = getAppearance(merged, display, name);
  	          if (PdfName.CH.equals(type)) {
  	            PdfNumber n = new PdfNumber((int)getPrivateBaseClassField("topFirst"));
  	            widget.put(PdfName.TI, n);
  	            merged.put(PdfName.TI, n);
  	          }
  	          PdfDictionary appDic = widget.getAsDict(PdfName.AP);
  	          if (appDic == null) {
  	            appDic = new PdfDictionary();
  	            
  	          }
  	          //changes for IS-4485 beginn 
  	          widget.put(PdfName.AP, appDic);
	          merged.put(PdfName.AP, appDic);
	          //changes for IS-4485 end
  	          appDic.put(PdfName.N, app.getIndirectReference());
  	          writer.releaseTemplate(app);
  	        } else {
  	          widget.remove(PdfName.AP);
  	          merged.remove(PdfName.AP);
  	        }
  	        markUsed(widget);
  	      }
  	      return true;
  	    } else if (PdfName.BTN.equals(type)) {
  	      PdfNumber ff = item.getMerged(0).getAsNumber(PdfName.FF);
  	      int flags = 0;
  	      if (ff != null) {
  	        flags = ff.intValue();
  	      }
  	      if ((flags & PdfFormField.FF_PUSHBUTTON) != 0) {
  	        //we'll assume that the value is an image in base64
  	        Image img;
  	        try {
  	          img = Image.getInstance(Base64.getDecoder().decode(value));
  	        } catch (Exception e) {
  	          return false;
  	        }
  	        PushbuttonField pb = getNewPushbuttonFromField(name);
  	        pb.setImage(img);
  	        replacePushbuttonField(name, pb.getField());
  	        return true;
  	      }
  	      PdfName v = new PdfName(value);
  	      List<String> lopt = new ArrayList<>();
  	      PdfArray opts = item.getValue(0).getAsArray(PdfName.OPT);
  	      if (opts != null) {
  	        for (int k = 0; k < opts.size(); ++k) {
  	          PdfString valStr = opts.getAsString(k);
  	          if (valStr != null) {
  	            lopt.add(valStr.toUnicodeString());
  	          } else {
  	            lopt.add(null);
  	          }
  	        }
  	      }
  	      int vidx = lopt.indexOf(value);
  	      PdfName vt;
  	      if (vidx >= 0) {
  	        vt = new PdfName(String.valueOf(vidx));
  	      } else {
  	        vt = v;
  	      }
  	      for (int idx = 0; idx < item.size(); ++idx) {
  	        merged = item.getMerged(idx);
  	        PdfDictionary widget = item.getWidget(idx);
  	        PdfDictionary valDict = item.getValue(idx);
  	        markUsed(item.getValue(idx));
  	        valDict.put(PdfName.V, vt);
  	        merged.put(PdfName.V, vt);
  	        markUsed(widget);
  	        if (isInAP(widget, vt)) {
  	          merged.put(PdfName.AS, vt);
  	          widget.put(PdfName.AS, vt);
  	        } else {
  	          merged.put(PdfName.AS, PdfName.Off);
  	          widget.put(PdfName.AS, PdfName.Off);
  	        }
  	      }
  	      return true;
  	    }
  	    return false;
    }
    
    /* Same as Acrofields.setListSelection except
     *a) calling custom getListAppearance instead of getAppearance so that the multiselect renders correctly when downloading pdf
     */
   @Override
    public boolean setListSelection(String name, String[] value) throws IOException, DocumentException {
    	Item item = getFieldItem(name);
        if (item == null)
            return false;
        PdfDictionary merged = item.getMerged( 0 );
        PdfName type = merged.getAsName(PdfName.FT);
        if (!PdfName.CH.equals(type)) {
        	return false;
        }
        String[] options = getListOptionExport(name);
        PdfArray array = new PdfArray();
        for (int i = 0; i < value.length; i++) {
        	for (int j = 0; j < options.length; j++) {
        		if (options[j].equals(value[i])) {
        			array.add(new PdfNumber(j));
        			break;
        		}
        	}
        }
        item.writeToAll(PdfName.I, array, Item.WRITE_MERGED | Item.WRITE_VALUE);
        
        PdfArray vals = new PdfArray();
        for (int i = 0; i < value.length; ++i) {
        	vals.add( new PdfString( value[i] ) );
        }
        item.writeToAll(PdfName.V, vals, Item.WRITE_MERGED | Item.WRITE_VALUE);
        
        PdfAppearance app = getListAppearance( merged, value, name ); //updated getAppearance to custom getListAppearance
        
       PdfDictionary apDic = new PdfDictionary();
        apDic.put( PdfName.N, app.getIndirectReference() );
        item.writeToAll(PdfName.AP, apDic, Item.WRITE_MERGED | Item.WRITE_WIDGET);
       
        writer.releaseTemplate( app );
     
        item.markUsed( this, Item.WRITE_VALUE | Item.WRITE_WIDGET );
        return true;
	}
   
    
/* Same as AcroFields.getAppearance except
 * a) Using InsignTextField to call getListAppearance to modify the default appearance of the multiselect
 * b) Renamed getAppearance to get getListAppearance so that it is not called in other scenarios
 */
 private  PdfAppearance getListAppearance(PdfDictionary merged, String values[], String fieldName) throws IOException, DocumentException {
    int topFirst = 0;
        String text = (values.length > 0) ? values[0] : null;
      Map  fieldCache=getFieldCache();
      InsignTextField tx = null;
        if (fieldCache == null || !fieldCache.containsKey(fieldName)) {
            tx = new InsignTextField(writer, null, null);
           // tx.setExtraMargin(extraMarginLeft, extraMarginTop);
            tx.setBorderWidth(0);
            tx.setSubstitutionFonts(getSubstitutionFonts());
            decodeGenericDictionary(merged, tx);
            //rect
            PdfArray rect = merged.getAsArray(PdfName.RECT);
            Rectangle box = PdfReader.getNormalizedRectangle(rect);
            if (tx.getRotation() == 90 || tx.getRotation() == 270)
                box = box.rotate();
            tx.setBox(box);
            if (fieldCache != null)
                fieldCache.put(fieldName, tx);
        }
        else {
        	tx = (InsignTextField) fieldCache.get(fieldName);
            tx.setWriter(writer);
        }
        PdfName fieldType = merged.getAsName(PdfName.FT);
        if (PdfName.TX.equals(fieldType)) {
            if (values.length > 0 && values[0] != null) {
                tx.setText(values[0]);
            }
            return tx.getAppearance();
        }
        if (!PdfName.CH.equals(fieldType))
            throw new DocumentException(MessageLocalization.getComposedMessage("an.appearance.was.requested.without.a.variable.text.field"));
        PdfArray opt = merged.getAsArray(PdfName.OPT);
        int flags = 0;
        PdfNumber nfl = merged.getAsNumber(PdfName.FF);
        if (nfl != null)
            flags = nfl.intValue();
        if ((flags & PdfFormField.FF_COMBO) != 0 && opt == null) {
            tx.setText(text);
            return tx.getAppearance();
        }
        if (opt != null) {
            String choices[] = new String[opt.size()];
            String choicesExp[] = new String[opt.size()];
            for (int k = 0; k < opt.size(); ++k) {
                PdfObject obj = opt.getPdfObject(k);
                if (obj.isString()) {
                    choices[k] = choicesExp[k] = ((PdfString)obj).toUnicodeString();
                }
                else {
                    PdfArray a = (PdfArray) obj;
                    choicesExp[k] = a.getAsString(0).toUnicodeString();
                    choices[k] = a.getAsString(1).toUnicodeString();
                }
            }
            if ((flags & PdfFormField.FF_COMBO) != 0) {
                for (int k = 0; k < choices.length; ++k) {
                    if (text.equals(choicesExp[k])) {
                        text = choices[k];
                        break;
                    }
                }
                tx.setText(text);
                return tx.getAppearance();
            }
            List<Integer> indexes = new ArrayList<Integer>();
            for (int k = 0; k < choicesExp.length; ++k) {
            	for (int j = 0; j < values.length; ++j) {
            		String val = values[j];
            		if (val != null && val.equals(choicesExp[k])) {
            			indexes.add( new Integer( k ) );
            			break;
            		}
            	}
            }
            tx.setChoices(choices);
            tx.setChoiceExports(choicesExp);
            tx.setChoiceSelections( indexes );
        }
        PdfAppearance app = tx.getListAppearance(); // this gets called
        topFirst = tx.getTopFirst();
        return app;
    }

 
    
    @Override
    //Beim lesen von Radios die "Options-Liste" ignorieren, weil dies bei doppelten Einträgen nicht richtig funktioniert (Bsp. Fam VP2 bei Signal Iduna PDF)
    public String getField(String name) {
    	String ret = super.getField(name);
    	Item item = (Item)getFields().get(name);
        if (item == null) {
			return null;
		}
    	PdfDictionary mergedDict = item.getMerged( 0 );
    	PdfName type = mergedDict.getAsName(PdfName.FT);
        PdfObject v = PdfReader.getPdfObject(mergedDict.get(PdfName.V));
        if (PdfName.BTN.equals(type)) {
            PdfNumber ff = mergedDict.getAsNumber(PdfName.FF);
            int flags = 0;
            if (ff != null) {
				flags = ff.intValue();
			}
            if ((flags & PdfFormField.FF_PUSHBUTTON) != 0) {
				return "";
			}
            String value = "";
            if (v instanceof PdfName) {
				value = PdfName.decodeName(v.toString());
			}
			else if (v instanceof PdfString) {
				value = ((PdfString)v).toUnicodeString();
			}
            return value;
        }
        //ISS-478 - Wenn kein Default gefunden wird, dann für Text beim Item selbst schauen (!merged)
        if (PdfName.TX.equals(type) && org.apache.commons.lang3.StringUtils.isEmpty(ret)) {
        	PdfDictionary dict = item.getValue( 0 );
        	if (dict != null) {
        		 PdfObject val = PdfReader.getPdfObject(dict.get(PdfName.V));
                 if (val instanceof PdfName) {
     				return PdfName.decodeName(val.toString());
     			}
     			else if (val instanceof PdfString) {
     				return ((PdfString)val).toUnicodeString();
     			}
        	}
        }
    	return ret;
    }
    protected Object getPrivateBaseClassField(String fieldName) {
		Field oFieldContext = null;
		try {
			oFieldContext = AcroFields.class.getDeclaredField(fieldName);
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
    
    @Override
	public PdfPKCS7 verifySignature(String name, String provider) {
        PdfDictionary v = getSignatureDictionary(name);
        if (v == null) {
          return null;
        }
        try {
          PdfName sub = v.getAsName(PdfName.SUBFILTER);
          PdfString contents = v.getAsString(PdfName.CONTENTS);
          InsignPdfPKCS7 pk = null;
          if (sub.equals(PdfName.ADBE_X509_RSA_SHA1)) {
            PdfString cert = v.getAsString(PdfName.CERT);
            pk = new InsignPdfPKCS7(contents.getOriginalBytes(), cert.getBytes(), provider);
          } else {
            pk = new InsignPdfPKCS7(contents.getOriginalBytes(), provider);
          }
          updateByteRange(pk, v);
          PdfString str = v.getAsString(PdfName.M);
          if (str != null) {
            pk.setSignDate(PdfDate.decode(str.toString()));
          }
          PdfObject obj = PdfReader.getPdfObject(v.get(PdfName.NAME));
          if (obj != null) {
            if (obj.isString()) {
              pk.setSignName(((PdfString) obj).toUnicodeString());
            } else if (obj.isName()) {
              pk.setSignName(PdfName.decodeName(obj.toString()));
            }
          }
          str = v.getAsString(PdfName.REASON);
          if (str != null) {
            pk.setReason(str.toUnicodeString());
          }
          str = v.getAsString(PdfName.LOCATION);
          if (str != null) {
            pk.setLocation(str.toUnicodeString());
          }
          return pk;
        } catch (Exception e) {
          throw new ExceptionConverter(e);
        }
      }
    
    private void updateByteRange(PdfPKCS7 pkcs7, PdfDictionary v) {
        PdfArray b = v.getAsArray(PdfName.BYTERANGE);
        RandomAccessFileOrArray rf = reader.getSafeFile();
        try {
          rf.reOpen();
          byte[] buf = new byte[8192];
          for (int k = 0; k < b.size(); ++k) {
            int start = b.getAsNumber(k).intValue();
            int length = b.getAsNumber(++k).intValue();
            rf.seek(start);
            while (length > 0) {
              int rd = rf.read(buf, 0, Math.min(length, buf.length));
              if (rd <= 0) {
                break;
              }
              length -= rd;
              pkcs7.update(buf, 0, rd);
            }
          }
        } catch (Exception e) {
          throw new ExceptionConverter(e);
        } finally {
          try {
            rf.close();
          } catch (Exception e) {
          }
        }
      }
    
    /**
     *  Fixes ISS-4584 -> NPex in Code
     */
    public String[] getListSelection(String name) {
        String[] ret;
        String s = getField(name);
        if (s == null) {
          ret = new String[]{};
        } else {
          ret = new String[]{s};
        }
        Item item = (Item) getFields().get(name);
        if (item == null) {
          return ret;
        }
        //PdfName type = (PdfName)PdfReader.getPdfObject(((PdfDictionary)item.merged.get(0)).get(PdfName.FT));
        //if (!PdfName.CH.equals(type)) {
        //    return ret;
        //}
        PdfArray values = item.getMerged(0).getAsArray(PdfName.I);
        if (values == null) {
          return ret;
        }
        ret = new String[values.size()];
        String[] options = getListOptionExport(name);
        PdfNumber n;
        int idx = 0;
        for (PdfObject pdfObject : values.getElements()) {
          n = (PdfNumber) pdfObject;
          if (options == null) {//FIX IS HERE: Use value itself instead of non-existing option
        	  ret[idx++] = n.intValue()+"";
          } else {
        	  ret[idx++] = options[n.intValue()];
          }
         
        }
        return ret;
      }
    public  HashMap getStdFieldFontNames() {
   	 HashMap<String, String[]> stdFieldFontNames = new HashMap<>();
   	 try {
   		 stdFieldFontNames = (HashMap<String, String[]>) getPrivateBaseClassField("stdFieldFontNames");
		} catch (Exception e) {
			logger.log(Level.SEVERE, "stdFieldFontNames",e);
		}
   	return stdFieldFontNames;
   }
}
