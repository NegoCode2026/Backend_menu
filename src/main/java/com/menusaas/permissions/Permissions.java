package com.menusaas.permissions;

/**
 * Permisos asignables por rol dentro de cada restaurante.
 * Cada restaurante crea su menú y decide qué rol edita qué.
 */
public final class Permissions {

    private Permissions() {
    }

    /** Crear/editar/borrar productos, categorías y subir imágenes. */
    public static final String MENU_EDIT = "MENU_EDIT";
    /** Editar datos de un pedido (cliente, mesa, ítems). */
    public static final String ORDERS_EDIT = "ORDERS_EDIT";
    /** Confirmar y entregar (mesero). */
    public static final String ORDER_SERVE = "ORDER_SERVE";
    /** Mandar a cocina y marcar listo. */
    public static final String ORDER_KITCHEN = "ORDER_KITCHEN";
    /** Cancelar pedidos. */
    public static final String ORDER_CANCEL = "ORDER_CANCEL";
    /** Cobrar pedidos entregados. */
    public static final String CASH_CHARGE = "CASH_CHARGE";
    /** Ver caja del día y cerrarla. */
    public static final String CASH_CLOSE = "CASH_CLOSE";
    /** Ajustar existencias y ver kardex. */
    public static final String INVENTORY_MANAGE = "INVENTORY_MANAGE";
    /** Ver ventas y ganancias. */
    public static final String REPORTS_VIEW = "REPORTS_VIEW";
    /** Gestionar el equipo (usuarios). */
    public static final String USERS_MANAGE = "USERS_MANAGE";
    /** Configurar restaurante, QR y suscripción. */
    public static final String SETTINGS_EDIT = "SETTINGS_EDIT";

    public static final java.util.List<String> ALL = java.util.List.of(
            MENU_EDIT, ORDERS_EDIT, ORDER_SERVE, ORDER_KITCHEN, ORDER_CANCEL,
            CASH_CHARGE, CASH_CLOSE, INVENTORY_MANAGE, REPORTS_VIEW,
            USERS_MANAGE, SETTINGS_EDIT);
}
