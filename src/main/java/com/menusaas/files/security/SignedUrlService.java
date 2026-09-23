package com.menusaas.files.security;

import com.menusaas.config.AppProperties;

/**
 * Alias de compatibilidad: la implementación vive en
 * {@link com.menusaas.shared.security.SignedUrlService}.
 *
 * @deprecated usar {@code com.menusaas.shared.security.SignedUrlService}.
 * Se mantiene para no romper imports externos; será eliminado en la próxima minor.
 */
@Deprecated(forRemoval = true)
public class SignedUrlService extends com.menusaas.shared.security.SignedUrlService {

    public SignedUrlService(AppProperties appProperties) {
        super(appProperties);
    }
}
