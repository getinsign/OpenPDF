package com.lowagie.text.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

public class InsignPdfReader extends PdfReader
{
	private InsignAcroFields insignAcroFields;

	public InsignPdfReader(final InputStream inputStream, final byte[] bs) throws IOException {
		super(inputStream, bs);
		setPasswordUsed();
	}

	public InsignPdfReader(final byte[] inputStream, final byte[] bs) throws IOException {
		super(inputStream, bs);
		setPasswordUsed();
	}
	
	/** Gets a read-only version of <CODE>AcroFields</CODE>.
     * @return a read-only version of <CODE>AcroFields</CODE>
     */
    @Override
	public AcroFields getAcroFields() {
    	if (insignAcroFields == null) {
    		insignAcroFields = new InsignAcroFields(this);
    	}
		return insignAcroFields;
    }
    
    @Override
	public PRAcroForm getAcroForm() {
        if (!acroFormParsed) {
          acroFormParsed = true;
          PdfObject form = catalog.get(PdfName.ACROFORM);
          if (form != null) {
            try {
              acroForm = new InSignPRAcroForm(this);
              acroForm.readAcroForm((PdfDictionary) getPdfObject(form));
            } catch (Exception e) {
              acroForm = null;
            }
          }
        }
        return acroForm;
      }

    @Override
    public PdfDictionary getCatalog()
    {
    	return super.getCatalog();
    }
    
    /**
     * Das ist nur ein Hack, weil isOpenedWithFullPermissions() final ist und er sonst
     * teilweise sofort mit BadPassword abbricht, obwohl er es gar nicht braucht
     * 
     * 
	 * @return void
	 * @author RolandW
	 * @since 21.03.2013
	 */
	protected void setPasswordUsed() {

		Field oFieldContext = null;
		try {
			oFieldContext = PdfReader.class.getDeclaredField("ownerPasswordUsed");
			oFieldContext.setAccessible(true);
			oFieldContext.set(this, Boolean.TRUE);

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
	
}
