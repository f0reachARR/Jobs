# BedrockDialog API

## Using as a Library

### 1. Declare the dependency

In your `paper-plugin.yml`:

```yaml
dependencies:
  server:
    BedrockDialog:
      load: BEFORE
      required: true
```

### 2. Initialize

```java
@Override
public void onEnable() {
  // You can omit calling this if you use BedrockDialog as a plugin dependency. In case of shadow(shaded) usage, you must call this method to initialize the API.
  BedrockDialog.init(this); 
}

@Override
public void onDisable() {
  BedrockDialog.reset();
}
```

### 3. Show a dialog

BedrockDialog automatically detects whether the player is on Java or Bedrock Edition and uses the appropriate backend.

```java
UnifiedDialog dialog = ConfirmDialog.builder()
    .title(Component.text("Are you sure?"))
    .body(Component.text("This action cannot be undone."))
    .yesLabel(Component.text("Confirm"))
    .noLabel(Component.text("Cancel"))
    .onYes(player -> {
        // Callbacks may be called off the main thread — schedule Bukkit API calls if needed
        Bukkit.getScheduler().runTask(plugin, () -> {
            // your logic here
        });
    })
    .onNo(player -> {})
    .build();

BedrockDialog.get().show(player, dialog);
```

### 4. Close a dialog

```java
BedrockDialog.get().closeDialog(player);
```

> **Note**: Programmatic close requires Floodgate not Geyser, due to API limitations.

## Dialog Types

| Type                | Description                                               |
|---------------------|-----------------------------------------------------------|
| `ConfirmDialog`     | Yes/No choice                                             |
| `NoticeDialog`      | Informational with a single dismiss button                |
| `MultiButtonDialog` | Multiple clickable buttons                                |
| `InputDialog`       | Form with text, slider, boolean, and dropdown fields      |

### InputDialog and InputResponse

```java
UnifiedDialog dialog = InputDialog.builder()
    .title(Component.text("Item Form"))
    .body(Component.text("Select what you want"))
    .submitLabel(Component.text("Submit"))
    .addInput(TextInput.builder().key("player_name").label("Target Player").build())
    .addInput(SliderInput.builder().key("amount").label("Amount").min(1).max(64).step(1).defaultValue(1).build())
    .addInput(DropdownInput.builder().key("item").label("Item")
        .addOption("diamond", "Diamond")
        .addOption("gold_ingot", "Gold Ingot")
        .build())
    .onSubmit((player, response) -> {
        String target = response.getText("player_name");
        float  amount = response.getFloat("amount");
        String itemId = response.getDropdownOptionId("item");
    })
    .onClose(player -> {})
    .build();
```

#### InputResponse methods

| Method                          | Description                             |
|---------------------------------|-----------------------------------------|
| `getText(key)`                  | Get text field value                    |
| `getFloat(key)`                 | Get slider value                        |
| `getBoolean(key)`               | Get boolean/toggle value                |
| `getDropdownOptionId(key)`      | Get selected dropdown option ID         |
| `getDropdownIndex(key)`         | Get selected dropdown index             |

### Button and input widths (Java Edition only)

Buttons and text/slider/dropdown inputs accept an optional `width` (pixels, 1-1024).
Bedrock Edition ignores width — its forms control layout themselves.

```java
ConfirmDialog.builder()
    .yesLabel(Component.text("Confirm")).yesWidth(180)
    .noLabel(Component.text("Cancel")).noWidth(120)
    .build();

NoticeDialog.builder().dismissLabel(Component.text("OK")).dismissWidth(100).build();

MultiButtonDialog.builder()
    .button(Component.text("Wide"), 300, p -> {})   // explicit width
    .button(Component.text("Default"), p -> {})     // platform default
    .build();

InputDialog.builder()
    .submitLabel(Component.text("Save")).submitWidth(180)
    .cancelLabel(Component.text("Cancel")).cancelWidth(120)
    .addInput(TextInput.builder("name").label(Component.text("Name")).width(240).build())
    .addInput(SliderInput.builder("amount").label(Component.text("Amount"))
        .min(1).max(64).width(240).build())
    .addInput(DropdownInput.builder("item").label(Component.text("Item"))
        .addOption("diamond", Component.text("Diamond")).width(240).build())
    .build();
```

In YAML configs, the same fields are exposed as `yes_width`, `no_width`, `dismiss_width`,
`submit_width`, `cancel_width`, and per-button/per-input `width:` keys.

## Platform Differences

| Feature                     | Java Edition (Paper) | Bedrock Edition (Geyser) |
|-----------------------------|----------------------|--------------------------|
| MiniMessage formatting      | Supported            | Stripped to plain text   |
| On-close callback           | Best-effort          | Not supported            |
| Programmatic close          | Supported            | Requires Floodgate       |
| Slider step (float)         | Supported            | Rounded to integer       |
| Button / input widths       | Supported (1-1024)   | Ignored                  |

> Callbacks may be invoked from a network thread on Bedrock Edition. Always schedule Bukkit API calls with `Bukkit.getScheduler().runTask(...)`.
