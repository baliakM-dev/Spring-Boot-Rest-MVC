package com.restmvc.beer_store.enums;

public enum AddressType {

    /** Trvalé bydlisko zákazníka — používa sa ako fallback pre DPH ak nie je shipping adresa. */
    PERMANENT,

    /** Doručovacia adresa — primárny zdroj krajiny pre výpočet DPH pri checkout-e. */
    SHIPPING
}
