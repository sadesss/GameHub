// Заполняйте TODO. c — переданная база; функции выполняют реальные запросы MongoDB.
// Служебный lab.js менять не требуется. Контракты и порядок опытов — в Задание.md.
globalThis.installValidator = async c => {
    return c.runCommand({
        collMod: "products",

        validator: {
            $jsonSchema: {
                bsonType: "object",

                required: [
                    "_id",
                    "name",
                    "category",
                    "price",
                    "variants"
                ],

                properties: {
                    _id: {
                        bsonType: "string"
                    },

                    name: {
                        bsonType: "string"
                    },

                    category: {
                        bsonType: "string"
                    },

                    price: {
                        bsonType: "decimal",
                        minimum: NumberDecimal("0")
                    },

                    variants: {
                        bsonType: "array",

                        items: {
                            bsonType: "object",

                            required: [
                                "size",
                                "stock"
                            ],

                            properties: {
                                size: {
                                    bsonType: "string"
                                },

                                stock: {
                                    bsonType: ["int", "long"],
                                    minimum: 0
                                }
                            }
                        }
                    }
                }
            }
        },

        validationLevel: "strict",
        validationAction: "error"
    });
};

// Возврат: Promise массива полных Product; порядок _id ASC; [] при отсутствии.
globalThis.searchProducts = async (c, {category, size, qty}) => {
    return c.products.find({
        category: category,
        variants: {
            $elemMatch: {
                size: size,
                stock: { $gte: qty }
            }
        }
    })
    .sort({ _id: 1 })
    .toArray();
};

// Технический helper: верните MongoDB cursor с фильтром, сортировкой и limit.
// Один и тот же запрос используется для результата и explain в опыте с индексом.
globalThis.historyCursor = (c, {userId, status}) => {
    return c.orders
        .find({
            userId: userId,
            status: status
        })
        .sort({
            createdAt: -1,
            _id: 1
        })
        .limit(20);
};
globalThis.history = async (c, input) => historyCursor(c, input).toArray();

// Заполните порядок полей и направления индекса для historyCursor.
globalThis.historyIndex = {
    userId: 1,
    status: 1,
    createdAt: -1,
    _id: 1
};
// Если добавили hint в historyCursor, измените флаг на true.
globalThis.usesHistoryHint = false;

// Возврат: Promise массива {sku, name, qty, revenue}; revenue — Decimal128.
globalThis.report = async (c, from, to) => {
    return c.orders.aggregate([
        {
            $match: {
                status: "paid",
                createdAt: {
                    $gte: from,
                    $lt: to
                }
            }
        },

        {
            $unwind: "$items"
        },

        {
            $group: {
                _id: "$items.sku",
                qty: {
                    $sum: "$items.qty"
                },
                revenue: {
                    $sum: {
                        $multiply: [
                            "$items.qty",
                            "$items.price"
                        ]
                    }
                }
            }
        },

        {
            $lookup: {
                from: "products",
                localField: "_id",
                foreignField: "_id",
                as: "product"
            }
        },

        {
            $unwind: "$product"
        },

        {
            $project: {
                _id: 0,
                sku: "$_id",
                name: "$product.name",
                qty: 1,
                revenue: 1
            }
        },

        {
            $sort: {
                revenue: -1,
                sku: 1
            }
        },

        {
            $limit: 5
        }
    ]).toArray();
};

// request: {operationId, userId, items:[{sku,size,qty},{sku,size,qty}]}.
// Входы соответствуют контракту задания. Два SKU различны; порядок items значим.
// Возврат: Promise созданного или ранее сохранённого Order.
// При отказе: throw HWError('ITEM_UNAVAILABLE') или HWError('REQUEST_CONFLICT').
// Сообщение ошибки свободное; сценарий проверяет только error.code.
// transaction(body) предоставлена: body получает c — базу в активной сессии.
// Все операции покупки внутри body выполняйте через этот c.
globalThis.purchase = async (request, trace = () => {}) => {
  return transaction(async c => {
    // После попытки каждого списания передавайте trace:
    // {event:'update_inside_transaction', sku, matched:r.matchedCount,
    //  modified:r.modifiedCount}, где r — настоящий результат updateOne.
    // Это позволяет показать первое успешное изменение перед abort.
            // 1. Проверяем, не выполнялась ли уже операция с таким operationId
            const existingOrder = await c.orders.findOne({
                _id: request.operationId
            });

            if (existingOrder) {
                // Сравниваем только бизнес-параметры исходного запроса:
                // userId, sku, size, qty и порядок items.
                const sameRequest =
                    Number(existingOrder.userId) === request.userId &&
                    existingOrder.items.length === request.items.length &&
                    existingOrder.items.every((savedItem, i) => {
                        const requestItem = request.items[i];

                        return savedItem.sku === requestItem.sku &&
                               savedItem.size === requestItem.size &&
                               Number(savedItem.qty) === requestItem.qty;
                    });

                if (!sameRequest) {
                    throw HWError(
                        "REQUEST_CONFLICT",
                        "operationId already exists with different parameters"
                    );
                }

                // Полностью идентичный повтор:
                // ничего больше не списываем и возвращаем старый заказ.
                return existingOrder;
            }

            // 2. Здесь будем собирать позиции будущего заказа.
            const orderItems = [];

            for (const item of request.items) {

                // 3. Списываем остаток.
                //
                // Очень важно: stock >= qty находится прямо в условии update,
                // поэтому проверка и списание являются одной атомарной
                // операцией над документом Product.
                const result = await c.products.updateOne(
                    {
                        _id: item.sku,

                        variants: {
                            $elemMatch: {
                                size: item.size,
                                stock: { $gte: item.qty }
                            }
                        }
                    },
                    {
                        $inc: {
                            "variants.$.stock": -item.qty
                        }
                    }
                );

                // lab.js требует показать настоящее обновление,
                // произошедшее внутри транзакции.
                trace({
                    event: "update_inside_transaction",
                    sku: item.sku,
                    matched: result.matchedCount,
                    modified: result.modifiedCount
                });

                // Нет SKU / размера / достаточного остатка.
                if (result.modifiedCount !== 1) {
                    throw HWError(
                        "ITEM_UNAVAILABLE",
                        `Item ${item.sku}/${item.size} is unavailable`
                    );
                }

                // 4. Получаем текущую цену товара.
                // Она станет исторической ценой позиции заказа.
                const product = await c.products.findOne(
                    { _id: item.sku },
                    { price: 1 }
                );

                orderItems.push({
                    sku: item.sku,
                    size: item.size,
                    qty: NumberInt(item.qty),
                    price: product.price
                });
            }

            // 5. Оба списания успешны → создаём заказ.
            const order = {
                _id: request.operationId,
                userId: NumberInt(request.userId),
                createdAt: new Date(),
                status: "created",
                items: orderItems
            };

            await c.orders.insertOne(order);

            return order;
        });
};

// Используйте именно переданный c: он связан с одним MongoDB-клиентом опыта.
// writeProbe: сохранить {_id:id, message} в c.probe с majority и j:true.
// Возврат — результат записи с acknowledged:true. Повтор вызова с тем же id
// должен быть безопасен: оболочка опыта может повторить запись после ошибки связи.
globalThis.writeProbe = async (c, id, message) => {
    return c.probe.updateOne(
        { _id: id },
        {
            $set: {
                message: message
            }
        },
        {
            upsert: true,
            writeConcern: {
                w: "majority",
                j: true
            }
        }
    );
};

// Возврат — документ c.probe с указанным _id либо null.

globalThis.readProbe = async (c, id) => {
    return c.probe.findOne({
        _id: id
    });
};
