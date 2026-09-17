package com.lifeadmin.extraction;

/**
 * Where a value came from (spec §8/§25). OCR = read off the document, AI = model-interpreted,
 * DERIVED = calculated (e.g. warranty end = purchase + term), USER = user-entered/verified.
 */
public enum FieldSource {
    OCR,
    AI,
    DERIVED,
    USER
}
