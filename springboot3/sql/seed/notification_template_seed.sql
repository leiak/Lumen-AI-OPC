-- Default templates (W49)
INSERT INTO opc_notification_template (id, code, channel, subject, body, vars_schema, version, enabled) VALUES
(1001, 'welcome', 'email',
 '欢迎加入 OPC 一人公司社区',
 'Hi ${nickname},<br>欢迎加入 OPC 智能社区!你的创业之路,我们用 AI 陪伴。<br><a href="${inviteUrl}">查看你的工作台</a>',
 '{"nickname":"string","inviteUrl":"url"}', 1, 1),
(1002, 'order_paid', 'sms',
 NULL,
 '【OPC】您的订单 ${orderNo} 已支付成功,金额 ¥${amount}',
 '{"orderNo":"string","amount":"decimal"}', 1, 1),
(1003, 'opportunity_assigned', 'inbox',
 '新商机待跟进',
 '客户 ${customerName} 创建了商机「${oppName}」,金额 ¥${amount},请尽快跟进。',
 '{"customerName":"string","oppName":"string","amount":"decimal"}', 1, 1),
(1004, 'inventory_low', 'all',
 '库存预警: ${productName}',
 '商品 ${productName} (SKU: ${sku}) 当前库存 ${stock} 件,低于预警值 ${threshold}。请及时补货。',
 '{"productName":"string","sku":"string","stock":"int","threshold":"int"}', 1, 1)
ON DUPLICATE KEY UPDATE subject=VALUES(subject), body=VALUES(body);