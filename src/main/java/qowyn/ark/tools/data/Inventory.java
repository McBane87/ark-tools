package qowyn.ark.tools.data;

import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static qowyn.ark.tools.CommonFunctions.iterable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonGenerator;

import qowyn.ark.GameObject;
import qowyn.ark.arrays.ArkArrayObjectReference;
import qowyn.ark.tools.DataManager;
import qowyn.ark.types.ArkName;
import qowyn.ark.types.ObjectReference;

public class Inventory {

  public List<Integer> inventoryItems = new ArrayList<>();

  public List<Integer> equippedItems = new ArrayList<>();

  public List<Integer> itemSlots = new ArrayList<>();

  public double lastInventoryRefreshTime;

  public Inventory(GameObject inventory) {

    List<ObjectReference> inventoryItemReferences = inventory.getPropertyValue("InventoryItems", ArkArrayObjectReference.class);
    if (inventoryItemReferences != null) {
      for (ObjectReference inventoryItem : inventoryItemReferences) {
        inventoryItems.add(inventoryItem.getObjectId());
      }
    }

    List<ObjectReference> equippedItemReferences = inventory.getPropertyValue("EquippedItems", ArkArrayObjectReference.class);
    if (equippedItemReferences != null) {
      for (ObjectReference equippedItem : equippedItemReferences) {
        equippedItems.add(equippedItem.getObjectId());
      }
    }

    List<ObjectReference> itemSlotReferences = inventory.getPropertyValue("ItemSlots", ArkArrayObjectReference.class);
    if (itemSlotReferences != null) {
      for (ObjectReference itemSlot : itemSlotReferences) {
        itemSlots.add(itemSlot.getObjectId());
      }
    }

    lastInventoryRefreshTime = inventory.findPropertyValue("LastInventoryRefreshTime", Double.class).orElse(0.0);
  }

  public static final SortedMap<String, WriterFunction<Inventory>> PROPERTIES = new TreeMap<>();

  static {
    /**
     * Inventory Properties
     */
    PROPERTIES.put("inventoryItems", (inventory, generator, context, writeEmpty) -> {
      if (writeEmpty || !inventory.inventoryItems.isEmpty()) {
        generator.writeArrayFieldStart("inventoryItems");
        for (int itemId: inventory.inventoryItems) {
          generator.writeNumber(itemId);
        }
        generator.writeEndArray();
      }
    });
    PROPERTIES.put("equippedItems", (inventory, generator, context, writeEmpty) -> {
      if (writeEmpty || !inventory.equippedItems.isEmpty()) {
        generator.writeArrayFieldStart("equippedItems");
        for (int itemId: inventory.equippedItems) {
          generator.writeNumber(itemId);
        }
        generator.writeEndArray();
      }
    });
    PROPERTIES.put("itemSlots", (inventory, generator, context, writeEmpty) -> {
      if (writeEmpty || !inventory.itemSlots.isEmpty()) {
        generator.writeArrayFieldStart("itemSlots");
        for (int itemId: inventory.itemSlots) {
          generator.writeNumber(itemId);
        }
        generator.writeEndArray();
      }
    });
    PROPERTIES.put("lastInventoryRefreshTime", (inventory, generator, context, writeEmpty) -> {
      if (writeEmpty || inventory.lastInventoryRefreshTime != 0.0) {
        generator.writeNumberField("lastInventoryRefreshTime", inventory.lastInventoryRefreshTime);
      }
    });
  }

  public static final List<WriterFunction<Inventory>> PROPERTIES_LIST = new ArrayList<>(PROPERTIES.values());

  private static final Comparator<Map.Entry<ArkName, Integer>> ITEM_MAP_COMPARATOR = comparing(Map.Entry::getValue, reverseOrder());

  public void writeInventory(JsonGenerator generator, DataContext context, boolean writeEmpty, boolean summary) throws IOException {
    List<GameObject> objects = context.getObjectContainer().getObjects();

    if (summary) {
  
      List<Item> items = new ArrayList<>();
      for (int itemId: inventoryItems) {
        if (itemId >= objects.size()) {
          continue;
        }

        GameObject itemObject = objects.get(itemId);
        if (!Item.isDefaultItem(itemObject)) {
          items.add(new Item(itemObject));
        }
      }
      for (int itemId: equippedItems) {
        if (itemId >= objects.size()) {
          continue;
        }

        GameObject itemObject = objects.get(itemId);
        if (!Item.isDefaultItem(itemObject)) {
          items.add(new Item(itemObject));
        }
      }

      writeInventorySummary(generator, items);
    } else {
      generator.writeStartObject();
  
      List<Item> items = new ArrayList<>();
      for (int itemId: inventoryItems) {
        if (itemId >= objects.size()) {
          continue;
        }

        GameObject itemObject = objects.get(itemId);
        if (!Item.isDefaultItem(itemObject)) {
          items.add(new Item(itemObject));
        }
      }

      generator.writeFieldName("items");
      writeInventoryLong(generator, context, items, writeEmpty);

      items.clear();
      for (int itemId: equippedItems) {
        if (itemId >= objects.size()) {
          continue;
        }

        GameObject itemObject = objects.get(itemId);
        if (!Item.isDefaultItem(itemObject)) {
          items.add(new Item(itemObject));
        }
      }

      generator.writeFieldName("equipped");
      writeInventoryLong(generator, context, items, writeEmpty);

      generator.writeEndObject();
    }
  }

  public static void writeInventorySummary(JsonGenerator generator, List<Item> items) throws IOException {
    Map<ArkName, Integer> itemMap = new HashMap<>();

    for (Item item : items) {
      itemMap.merge(item.className, item.quantity, Integer::sum);
    }

    generator.writeStartArray();

    for (Map.Entry<ArkName, Integer> entry: iterable(itemMap.entrySet().stream().sorted(ITEM_MAP_COMPARATOR))) {
      generator.writeStartObject();

      String name = entry.getKey().toString();
      if (DataManager.hasItem(name)) {
        name = DataManager.getItem(name).getName();
      }

      generator.writeStringField("name", name);
      generator.writeNumberField("count", entry.getValue());

      generator.writeEndObject();
    }

    generator.writeEndArray();
  }

  public static void writeInventoryLong(JsonGenerator generator, DataContext context, List<Item> items, boolean writeEmpty) throws IOException {
    generator.writeStartArray();

    items.sort(Comparator.comparing(item -> DataManager.hasItem(item.className.toString()) ? DataManager.getItem(item.className.toString()).getName() : item.className.toString()));
    for (Item item : items) {
      generator.writeStartObject();

      item.writeAllProperties(generator, context, writeEmpty);

      /*
      String name = item.className.toString();
      if (DataManager.hasItem(name)) {
        name = DataManager.getItem(name).getName();
      }

      generator.writeStringField("name", name);

      if (blueprintStatus) {
        generator.writeBooleanField("isBlueprint", item.isBlueprint);
      }

      if (item.quantity > 1) {
        generator.writeNumberField("quantity", item.quantity);
      }

      if (!item.customName.isEmpty()) {
        generator.writeStringField("customName", item.customName);
      }

      if (!item.customDescription.isEmpty()) {
        generator.writeStringField("customDescription", item.customDescription);
      }

      if (!item.isBlueprint && item.durability > 0.0f) {
        generator.writeNumberField("durability", item.durability);
      }

      if (item.rating > 0.0f) {
        generator.writeNumberField("rating", item.rating);
      }

      if (item.quality > 0) {
        generator.writeNumberField("quality", item.quality);
      }

      if (item.itemStatValues[1] != 0) {
        generator.writeNumberField("armorMultiplier", 1.0f + ((float) Short.toUnsignedInt(item.itemStatValues[1])) * 0.2f * 0.001f);
      }

      if (item.itemStatValues[2] != 0) {
        generator.writeNumberField("durabilityMultiplier", 1.0f + ((float) Short.toUnsignedInt(item.itemStatValues[2])) * 0.25f * 0.001f);
      }

      if (item.itemStatValues[3] != 0) {
        generator.writeNumberField("damageMultiplier", 1.0f + ((float) Short.toUnsignedInt(item.itemStatValues[3])) * 0.1f * 0.001f);
      }

      if (item.itemStatValues[5] != 0) {
        generator.writeNumberField("hypoMultiplier", 1.0f + ((float) Short.toUnsignedInt(item.itemStatValues[5])) * 0.2f * 0.001f);
      }

      if (item.itemStatValues[7] != 0) {
        generator.writeNumberField("hyperMultiplier", 1.0f + ((float) Short.toUnsignedInt(item.itemStatValues[7])) * 0.2f * 0.001f);
      }

      if (item.className.toString().contains("_Fertilized_")) {
        generator.writeObjectFieldStart("eggAttributes");

        for (int i = 0; i < item.eggLevelups.length; i++) {
          byte value = item.eggLevelups[i];
          if (value != 0) {
            generator.writeNumberField(AttributeNames.get(i), Byte.toUnsignedInt(value));
          }
        }

        generator.writeEndObject();

        generator.writeObjectFieldStart("eggColors");

        for (int i = 0; i < item.eggColors.length; i++) {
          byte value = item.eggColors[i];
          if (value != 0) {
            generator.writeNumberField(Integer.toString(i), Byte.toUnsignedInt(value));
          }
        }

        generator.writeEndObject();
      }*/

      generator.writeEndObject();
    }
    generator.writeEndArray();
  }

}
