package com.menusaas.inventory.entity;

public enum MovementReason {
    /** Salida por pedido de cliente. */
    ORDER,
    /** Entrada por cancelación de pedido. */
    CANCEL_RESTORE,
    /** Entrada por reposición de mercancía. */
    RESTOCK,
    /** Ajuste manual (conteo físico, merma, etc.). */
    ADJUST
}
