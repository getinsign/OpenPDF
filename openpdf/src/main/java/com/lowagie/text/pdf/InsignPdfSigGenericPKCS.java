/**
 * @author roland.wachinger
 * @since 26.03.2021
 */
package com.lowagie.text.pdf;

import java.io.ByteArrayOutputStream;
import java.security.PrivateKey;
import java.security.cert.CRL;
import java.security.cert.Certificate;

import com.lowagie.text.ExceptionConverter;


public abstract class InsignPdfSigGenericPKCS extends PdfSignature {
    /**
     * The hash algorithm, for example "SHA1"
     */
    protected String hashAlgorithm;
    /**
     * The crypto provider
     */
    protected String provider = null;
    /**
     * The class instance that calculates the PKCS#1 and PKCS#7
     */
    protected InsignPdfPKCS7 pkcs;
    /**
     * The subject name in the signing certificate (the element "CN")
     */
    protected String   name;

    private byte[] externalDigest;
    private byte[] externalRSAdata;
    private String digestEncryptionAlgorithm;

    /**
     * Creates a generic standard filter.
     * @param filter the filter name
     * @param subFilter the sub-filter name
     */
    public InsignPdfSigGenericPKCS(PdfName filter, PdfName subFilter) {
        super(filter, subFilter);
    }

    /**
     * Sets the crypto information to sign.
     * @param privKey the private key
     * @param certChain the certificate chain
     * @param crlList the certificate revocation list. It can be <CODE>null</CODE>
     */
    public void setSignInfo(PrivateKey privKey, Certificate[] certChain, CRL[] crlList) {
        try {
            pkcs = new InsignPdfPKCS7(privKey, certChain, crlList, hashAlgorithm, provider, PdfName.ADBE_PKCS7_SHA1.equals(get(PdfName.SUBFILTER)));
            pkcs.setExternalDigest(externalDigest, externalRSAdata, digestEncryptionAlgorithm);
            if (PdfName.ADBE_X509_RSA_SHA1.equals(get(PdfName.SUBFILTER))) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                for (Certificate certificate : certChain) {
                    bout.write(certificate.getEncoded());
                }
                bout.close();
                setCert(bout.toByteArray());
                setContents(pkcs.getEncodedPKCS1());
            }
            else
                setContents(pkcs.getEncodedPKCS7());
            name = PdfPKCS7.getSubjectFields(pkcs.getSigningCertificate()).getField("CN");
            if (name != null)
                put(PdfName.NAME, new PdfString(name, PdfObject.TEXT_UNICODE));
            pkcs = new InsignPdfPKCS7(privKey, certChain, crlList, hashAlgorithm, provider, PdfName.ADBE_PKCS7_SHA1.equals(get(PdfName.SUBFILTER)));
            pkcs.setExternalDigest(externalDigest, externalRSAdata, digestEncryptionAlgorithm);
        }
        catch (Exception e) {
            throw new ExceptionConverter(e);
        }
    }

    /**
     * Sets the digest/signature to an external calculated value.
     * @param digest the digest. This is the actual signature
     * @param RSAdata the extra data that goes into the data tag in PKCS#7
     * @param digestEncryptionAlgorithm the encryption algorithm. It may must be <CODE>null</CODE> if the <CODE>digest</CODE>
     * is also <CODE>null</CODE>. If the <CODE>digest</CODE> is not <CODE>null</CODE>
     * then it may be "RSA" or "DSA"
     */
    public void setExternalDigest(byte[] digest, byte[] RSAdata, String digestEncryptionAlgorithm) {
        externalDigest = digest;
        externalRSAdata = RSAdata;
        this.digestEncryptionAlgorithm = digestEncryptionAlgorithm;
    }

    /**
     * Gets the subject name in the signing certificate (the element "CN")
     * @return the subject name in the signing certificate (the element "CN")
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the class instance that does the actual signing.
     * @return the class instance that does the actual signing
     */
    public PdfPKCS7 getSigner() {
        return pkcs;
    }

    /**
     * Gets the signature content. This can be a PKCS#1 or a PKCS#7. It corresponds to
     * the /Contents key.
     * @return the signature content
     */
    public byte[] getSignerContents() {
        if (PdfName.ADBE_X509_RSA_SHA1.equals(get(PdfName.SUBFILTER)))
            return pkcs.getEncodedPKCS1();
        else
            return pkcs.getEncodedPKCS7();
    }

    /**
     * Creates a standard filter of the type VeriSign.
     */
    public static class VeriSign extends InsignPdfSigGenericPKCS {
        /**
         * The constructor for the default provider.
         */
        public VeriSign() {
            super(PdfName.VERISIGN_PPKVS, PdfName.ADBE_PKCS7_DETACHED);
            hashAlgorithm = "MD5";
            put(PdfName.R, new PdfNumber(65537));
        }

        /**
         * The constructor for an explicit provider.
         * @param provider the crypto provider
         */
        public VeriSign(String provider) {
            this();
            this.provider = provider;
        }
    }

    /**
     * Creates a standard filter of the type self signed.
     */
    public static class PPKLite extends InsignPdfSigGenericPKCS {
        /**
         * The constructor for the default provider.
         */
        public PPKLite() {
            super(PdfName.ADOBE_PPKLITE, PdfName.ADBE_X509_RSA_SHA1);
            this.hashAlgorithm = "SHA1";
            put(PdfName.R, new PdfNumber(65541));
        }

        /**
         * The constructor for an explicit provider.
         * @param provider the crypto provider
         */
        public PPKLite(String provider) {
            this();
            this.provider = provider;
        }
    }

    /**
     * Creates a standard filter of the type Windows Certificate.
     */
    public static class PPKMS extends InsignPdfSigGenericPKCS {
        /**
         * The constructor for the default provider.
         */
        public PPKMS() {
            super(PdfName.ADOBE_PPKMS, PdfName.ADBE_PKCS7_SHA1);
            hashAlgorithm = "SHA1";
        }

        /**
         * The constructor for an explicit provider.
         * @param provider the crypto provider
         */
        public PPKMS(String provider) {
            this();
            this.provider = provider;
        }
    }
    
    public static class INSIGN extends InsignPdfSigGenericPKCS {
        
        public INSIGN(String hashAlgo) {
            super(PdfName.ADOBE_PPKLITE, PdfName.ADBE_PKCS7_DETACHED);
            hashAlgorithm = hashAlgo;
        }

        public INSIGN(String provider, String hashAlgo) {
            this(hashAlgo);
            this.provider = provider;
        }
    }
}
