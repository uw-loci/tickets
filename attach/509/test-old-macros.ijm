id="/Volumes/DATA/al3d/franz/Iron Plate.al3d";

// merge_channels:
run("Bio-Formats Importer", "open=[" + id + "] merge_channels view=[Standard ImageJ] stack_order=XYCZT");
run("Bio-Formats Importer", "open=[" + id + "] color_mode=Composite view=Hyperstack stack_order=XYCZT");

// rgb_colorize:
run("Bio-Formats Importer", "open=[" + id + "] rgb_colorize view=Hyperstack stack_order=XYCZT");
run("Bio-Formats Importer", "open=[" + id + "] color_mode=Colorized view=Hyperstack stack_order=XYCZT");

// custom_colorize:
run("Bio-Formats Importer", "open=[" + id + "] custom_colorize view=Hyperstack stack_order=XYCZT series_0_channel_0_red=231 series_0_channel_0_green=100 series_0_channel_0_blue=136 series_0_channel_1_red=143 series_0_channel_1_green=214 series_0_channel_1_blue=100 series_0_channel_2_red=240 series_0_channel_2_green=200 series_0_channel_2_blue=120");
run("Bio-Formats Importer", "open=[" + id + "] color_mode=Custom view=Hyperstack stack_order=XYCZT series_0_channel_0_red=231 series_0_channel_0_green=100 series_0_channel_0_blue=136 series_0_channel_1_red=143 series_0_channel_1_green=214 series_0_channel_1_blue=100 series_0_channel_2_red=240 series_0_channel_2_green=200 series_0_channel_2_blue=120");
