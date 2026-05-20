/**
 * Re-export of AdminProductPanelHandler.
 *
 * AdminProductPanelHandler replaces ProductManagementHandler as the dedicated
 * admin product management interaction handler. This module is kept for
 * backward compatibility — it simply re-exports AdminProductPanelHandler.
 *
 * @deprecated Use AdminProductPanelHandler instead. The duplicate registration
 * has been removed from AdminModule — only AdminProductPanelHandler is registered.
 * See P0-13 for details.
 */
export { AdminProductPanelHandler as ProductManagementHandler, } from '../product/AdminProductPanelHandler.js';
//# sourceMappingURL=ProductManagementHandler.js.map