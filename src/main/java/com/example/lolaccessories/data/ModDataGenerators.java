package com.example.lolaccessories.data;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 数据生成入口：把各个 Provider 挂到数据生成器上。
 * 运行 ./gradlew runData 后，产物输出到 src/generated/resources/。
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModDataGenerators {

    private ModDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        // 客户端资源：语言文件、物品模型
        generator.addProvider(event.includeClient(), new ModLanguageProvider(packOutput, "en_us"));
        generator.addProvider(event.includeClient(), new ModLanguageProvider(packOutput, "zh_cn"));
        generator.addProvider(event.includeClient(), new ModItemModelProvider(packOutput, existingFileHelper));

        // 服务端资源：配方、Curios 栏位物品标签、装备品阶标签、成就进度与背景纹理
        generator.addProvider(event.includeServer(), new ModRecipeProvider(packOutput));
        generator.addProvider(event.includeServer(),
                new ModCurioTagsProvider(packOutput, event.getLookupProvider(), existingFileHelper));
        generator.addProvider(event.includeServer(),
                new ModGearTierTagsProvider(packOutput, event.getLookupProvider(), existingFileHelper));
        generator.addProvider(event.includeServer(), new ModAdvancementProvider(packOutput));
    }
}
