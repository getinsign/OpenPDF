package com.lowagie.text.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CRL;
import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTCTime;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;

import com.lowagie.text.ExceptionConverter;

public class InsignPdfPKCS7 extends PdfPKCS7 {

	private static Logger logger = Logger.getLogger(InsignPdfPKCS7.class.getName());
	private static HashSet<String> digestCorrection = new HashSet<String>();

	static {
		try {
			Field algorithmNamesField = PdfPKCS7.class.getDeclaredField("algorithmNames");
			algorithmNamesField.setAccessible(true);
			Map<String, String> algorithmNames = (Map<String, String>) algorithmNamesField.get(null);
			algorithmNames.put("1.2.840.10045.4.1", "ECDSA");
			algorithmNames.put("1.2.840.10045.4.3.1", "ECDSA");
			algorithmNames.put("1.2.840.10045.4.3.2", "ECDSA");
			algorithmNames.put("1.2.840.10045.4.3.3", "ECDSA");
			algorithmNames.put("1.2.840.10045.4.3.4", "ECDSA");
			algorithmNames.put("1.2.840.113549.1.1.10", "RSA/PSS");
			algorithmNames.put("1.2.840.113549.1.1.11", "RSA");
			algorithmNames.put("1.2.840.113549.1.1.12", "RSA");
			algorithmNames.put("1.2.840.113549.1.1.13", "RSA");
			algorithmNames.put("1.2.840.113549.1.1.14", "RSA");
			
			// exception for rsassa-pss so that old digest logic gets used
			digestCorrection.add("1.2.840.113549.1.1.10");
		}
		catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException
				| SecurityException e) {
			logger.log(Level.SEVERE,"init",e);
		}
	}
	
	public InsignPdfPKCS7(PrivateKey privKey, Certificate[] certChain, CRL[] crlList, String hashAlgorithm, String provider, boolean hasRSAdata)
		      throws InvalidKeyException, NoSuchProviderException, NoSuchAlgorithmException {
		super(privKey, certChain, crlList, hashAlgorithm, provider, hasRSAdata);
		this.hashAlgorithm = hashAlgorithm;
		
	}
	public InsignPdfPKCS7(byte[] originalBytes, String provider) {
		super(originalBytes, provider);
	}
	
	public InsignPdfPKCS7(byte[] originalBytes, byte[] bytes, String provider) {
		super(originalBytes, bytes, provider);
	}

	private static final String ID_PKCS7_DATA = "1.2.840.113549.1.7.1";
	private static final String ID_PKCS7_SIGNED_DATA = "1.2.840.113549.1.7.2";
	private static final String ID_RSA = "1.2.840.113549.1.1.1";
	private static final String ID_DSA = "1.2.840.10040.4.1";
	private static final String ID_CONTENT_TYPE = "1.2.840.113549.1.9.3";
	private static final String ID_MESSAGE_DIGEST = "1.2.840.113549.1.9.4";
	private static final String ID_SIGNING_TIME = "1.2.840.113549.1.9.5";
	private static final String ID_ADBE_REVOCATION = "1.2.840.113583.1.1.8";
	
	private String hashAlgorithm;
	
	
	protected void setObjectInBaseClass(Object obj, String filedname) {
		Field oFieldContext = null;
        try {
			oFieldContext = PdfPKCS7.class.getDeclaredField(filedname);
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
			oFieldContext = PdfPKCS7.class.getDeclaredField(fieldName);
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
	public byte[] getEncodedPKCS7(byte secondDigest[], Calendar signingTime,
		      TSAClient tsaClient, byte[] ocsp) {
		    try {
		      if (getPrivateBaseClassField("externalDigest") != null) {
		    	  setObjectInBaseClass(getPrivateBaseClassField("externalDigest") ,"digest");
		        if (getPrivateBaseClassField("RSAdata") != null)
		        	setObjectInBaseClass(getPrivateBaseClassField("externalRSAdata"), "RSAdata");
		      } else if (getPrivateBaseClassField("externalRSAdata") != null && getPrivateBaseClassField("RSAdata") != null) {
		    	  setObjectInBaseClass(getPrivateBaseClassField("externalRSAdata"), "RSAdata");
		    	  Signature sig = (Signature) getPrivateBaseClassField("sig");
		        sig.update((byte) getPrivateBaseClassField("RSAdata"));
		        setObjectInBaseClass(sig.sign(), "digest");
		      } else {
		    	  Signature sig = (Signature) getPrivateBaseClassField("sig");
		        if (getPrivateBaseClassField("RSAdata") != null) {
		        	MessageDigest messageDigest = (MessageDigest) getPrivateBaseClassField("messageDigest");
		        	setObjectInBaseClass(messageDigest.digest(), "RSAdata");
		        	
		          sig.update(messageDigest.digest());
		        }
		        setObjectInBaseClass(sig.sign(), "digest");
		      }

		      // Create the set of Hash algorithms
		      ASN1EncodableVector digestAlgorithms = new ASN1EncodableVector();
		      Set digestalgos = (Set) getPrivateBaseClassField("digestalgos");
		      for (Iterator it = digestalgos.iterator(); it.hasNext();) {
		        ASN1EncodableVector algos = new ASN1EncodableVector();
		        algos.add(new ASN1ObjectIdentifier((String) it.next()));
		        algos.add(DERNull.INSTANCE);
		        digestAlgorithms.add(new DERSequence(algos));
		      }

		      // Create the contentInfo.
		      ASN1EncodableVector v = new ASN1EncodableVector();
		      v.add(new ASN1ObjectIdentifier(ID_PKCS7_DATA));
		      byte RSAdata[] = (byte[]) getPrivateBaseClassField("RSAdata");
		      if (RSAdata != null)
		        v.add(new DERTaggedObject(0, new DEROctetString(RSAdata)));
		      DERSequence contentinfo = new DERSequence(v);

		      // Get all the certificates
		      //
		      
		      v = new ASN1EncodableVector();
		      Collection certs = (Collection) getPrivateBaseClassField("certs");
			for (Iterator i = certs.iterator(); i.hasNext();) {
		        try(ASN1InputStream tempstream = new ASN1InputStream(
		            new ByteArrayInputStream(((X509Certificate) i.next()).getEncoded()))) {
		        	v.add(tempstream.readObject());
		        };
		        
		      }

		      DERSet dercertificates = new DERSet(v);

		      // Create signerinfo structure.
		      //
		      ASN1EncodableVector signerinfo = new ASN1EncodableVector();

		      Integer signerversion = (Integer) getPrivateBaseClassField("signerversion");
			// Add the signerInfo version
		      //
		      signerinfo.add(new ASN1Integer(signerversion ));

		      v = new ASN1EncodableVector();
		      X509Certificate signCert = (X509Certificate) getPrivateBaseClassField("signCert");
		      v.add(getIssuer(signCert.getIssuerX500Principal()));
		      v.add(new ASN1Integer(signCert.getSerialNumber()));
		      signerinfo.add(new DERSequence(v));

		      // Add the digestAlgorithm
		      v = new ASN1EncodableVector();
	          String digestAlgorithm = (String) getPrivateBaseClassField("digestAlgorithm");
			  v.add(new ASN1ObjectIdentifier(digestAlgorithm));
	          v.add(DERNull.INSTANCE);
	          signerinfo.add(new DERSequence(v));

		      // add the authenticated attribute if present
		      if (secondDigest != null && signingTime != null) {
		        signerinfo.add(new DERTaggedObject(false, 0,
		            getAuthenticatedAttributeSet(secondDigest, signingTime, ocsp)));
		      }
		      // Add the digestEncryptionAlgorithm
		      v = new ASN1EncodableVector();
		      
		      if (digestCorrection.contains(signCert.getSigAlgOID())) {
		    	  String digestEncryptionAlgorithm = (String) getPrivateBaseClassField("digestEncryptionAlgorithm");
				  v.add(new ASN1ObjectIdentifier(digestEncryptionAlgorithm ));
		      } else {
		    	  v.add(new ASN1ObjectIdentifier(signCert.getSigAlgOID()));
		      }
		      v.add(DERNull.INSTANCE);
		      signerinfo.add(new DERSequence(v));

		      byte[] digest = (byte[]) getPrivateBaseClassField("digest");
			// Add the digest
		      signerinfo.add(new DEROctetString(digest ));

		      if (tsaClient != null) {
		        byte[] tsImprint = MessageDigest.getInstance(hashAlgorithm).digest(digest);
		        byte[] tsToken = tsaClient.getTimeStampToken(this, tsImprint);
		        if (tsToken != null) {
		          ASN1EncodableVector unauthAttributes = buildUnauthenticatedAttributes(tsToken);
		          if (unauthAttributes != null) {
		            signerinfo.add(new DERTaggedObject(false, 1, new DERSet(
		                unauthAttributes)));
		          }
		        }
		      }

		      // Finally build the body out of all the components above
		      ASN1EncodableVector body = new ASN1EncodableVector();
		      body.add(new ASN1Integer(getVersion()));
		      body.add(new DERSet(digestAlgorithms));
		      body.add(contentinfo);
		      body.add(new DERTaggedObject(false, 0, dercertificates));

		      // Only allow one signerInfo
		      body.add(new DERSet(new DERSequence(signerinfo)));

		      // Now we have the body, wrap it in it's PKCS7Signed shell
		      // and return it
		      //
		      ASN1EncodableVector whole = new ASN1EncodableVector();
		      whole.add(new ASN1ObjectIdentifier(ID_PKCS7_SIGNED_DATA));
		      whole.add(new DERTaggedObject(0, new DERSequence(body)));

		      ByteArrayOutputStream bOut = new ByteArrayOutputStream();

		      ASN1OutputStream dout = ASN1OutputStream.create(bOut);
		      dout.writeObject(new DERSequence(whole));
		      dout.close();

		      return bOut.toByteArray();
		    } catch (Exception e) {
		      throw new ExceptionConverter(e);
		    }
		  }
	
	private ASN1EncodableVector buildUnauthenticatedAttributes(
		      byte[] timeStampToken) throws IOException {
	    if (timeStampToken == null)
	      return null;
	
	    // @todo: move this together with the rest of the defintions
	    String ID_TIME_STAMP_TOKEN = "1.2.840.113549.1.9.16.2.14"; // RFC 3161
	                                                               // id-aa-timeStampToken
	    
	    ASN1EncodableVector unauthAttributes = new ASN1EncodableVector();
		
	    ASN1EncodableVector v = new ASN1EncodableVector();
	    try(ASN1InputStream tempstream = new ASN1InputStream(new ByteArrayInputStream(
	        timeStampToken))) {
	    	 v.add(new ASN1ObjectIdentifier(ID_TIME_STAMP_TOKEN)); // id-aa-timeStampToken
	 	    ASN1Sequence seq = (ASN1Sequence) tempstream.readObject();
	 	    v.add(new DERSet(seq));
	    }
	
	    unauthAttributes.add(new DERSequence(v));
	    return unauthAttributes;
	}
	private DERSet getAuthenticatedAttributeSet(byte secondDigest[],
		      Calendar signingTime, byte[] ocsp) {
		    try {
		      ASN1EncodableVector attribute = new ASN1EncodableVector();
		      ASN1EncodableVector v = new ASN1EncodableVector();
		      v.add(new ASN1ObjectIdentifier(ID_CONTENT_TYPE));
		      v.add(new DERSet(new ASN1ObjectIdentifier(ID_PKCS7_DATA)));
		      attribute.add(new DERSequence(v));
		      v = new ASN1EncodableVector();
		      v.add(new ASN1ObjectIdentifier(ID_SIGNING_TIME));
		      v.add(new DERSet(new DERUTCTime(signingTime.getTime())));
		      attribute.add(new DERSequence(v));
		      v = new ASN1EncodableVector();
		      v.add(new ASN1ObjectIdentifier(ID_MESSAGE_DIGEST));
		      v.add(new DERSet(new DEROctetString(secondDigest)));
		      attribute.add(new DERSequence(v));
		      Collection crls = (Collection) getPrivateBaseClassField("crls");
			if (ocsp != null) {
		        v = new ASN1EncodableVector();
		        v.add(new ASN1ObjectIdentifier(ID_ADBE_REVOCATION));
		        DEROctetString doctet = new DEROctetString(ocsp);
		        ASN1EncodableVector vo1 = new ASN1EncodableVector();
		        ASN1EncodableVector v2 = new ASN1EncodableVector();
		        v2.add(OCSPObjectIdentifiers.id_pkix_ocsp_basic);
		        v2.add(doctet);
		        ASN1Enumerated den = new ASN1Enumerated(0);
		        ASN1EncodableVector v3 = new ASN1EncodableVector();
		        v3.add(den);
		        v3.add(new DERTaggedObject(true, 0, new DERSequence(v2)));
		        vo1.add(new DERSequence(v3));
		        v.add(new DERSet(new DERSequence(new DERTaggedObject(true, 1,
		            new DERSequence(vo1)))));
		        attribute.add(new DERSequence(v));
		      } else if (!crls .isEmpty()) {
		        v = new ASN1EncodableVector();
		        v.add(new ASN1ObjectIdentifier(ID_ADBE_REVOCATION));
		        ASN1EncodableVector v2 = new ASN1EncodableVector();
		        for (Iterator i = crls.iterator(); i.hasNext();) {
		          try(
		          ASN1InputStream t = new ASN1InputStream(new ByteArrayInputStream(
		              ((X509CRL) i.next()).getEncoded()))) {
		        	  v2.add(t.readObject());
		          }
		          
		        }
		        v.add(new DERSet(new DERSequence(new DERTaggedObject(true, 0,
		            new DERSequence(v2)))));
		        attribute.add(new DERSequence(v));
		      }
		      return new DERSet(attribute);
		    } catch (Exception e) {
		      throw new ExceptionConverter(e);
		    }
		  }
	private static ASN1Primitive getIssuer(X500Principal principal) {
	    try {
          byte[] enc = principal.getEncoded();
	      try( ASN1InputStream in = new ASN1InputStream(new ByteArrayInputStream(enc))) {
	    	  ASN1Sequence seq = (ASN1Sequence) in.readObject();
		      return seq;
	      }
	    } catch (IOException e) {
	      throw new ExceptionConverter(e);
	    }
	  }
	
	 /**
     * Gets the bytes for the PKCS#1 object.
     *
     * @return a byte array
     */
    @Override
	public byte[] getEncodedPKCS1() {
        try {
        	byte[] digest;
        	 if (getPrivateBaseClassField("externalDigest") != null) {
        		 digest = (byte[]) getPrivateBaseClassField("externalDigest");
		    	  
        	 }
        	 else {
        		 Signature sig = (Signature) getPrivateBaseClassField("sig");
        		 digest = sig.sign();
        	 }
        	 setObjectInBaseClass(digest ,"digest");
            ByteArrayOutputStream bOut = new ByteArrayOutputStream();

            ASN1OutputStream dout = ASN1OutputStream.create(bOut);
            Method writeImplicitObject = dout.getClass().getDeclaredMethod("writeImplicitObject", ASN1Primitive.class);
            writeImplicitObject.setAccessible(true);
            writeImplicitObject.invoke(dout, new DEROctetString(digest));
            dout.close();

            return bOut.toByteArray();
        } catch (Exception e) {
            throw new ExceptionConverter(e);
        }
    }
    
    public static Map<String, String> getAllowedDigests() {
    	Map<String, String> allowedDigests = new HashMap<>();
    	try {
			Field allowedDigestsField = PdfPKCS7.class.getDeclaredField("allowedDigests");
			allowedDigestsField.setAccessible(true);
			allowedDigests= (Map<String, String>) allowedDigestsField.get(null);
	    }
		catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			//should never happen
		}
		return allowedDigests;
	}
    
    public static String getHashAlgoFromCert(final X509Certificate cert) {
		String algo = cert.getSigAlgName();
		Map<String, String> allowedDigests = getAllowedDigests();
		Optional<String> digestOpt = Arrays.asList(algo.toUpperCase().split("_?WITH_?")).stream().filter(s -> allowedDigests.containsKey(s)).findFirst();
		String digest = digestOpt.orElse("SHA512");//Else should never happen, best guess fallback
		return digest;
	}
    

}
