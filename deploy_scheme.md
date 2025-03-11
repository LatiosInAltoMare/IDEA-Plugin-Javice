# Javice模型部署细节

## 满血版Deepseek部署

满血版Deepseek通过调用[火山引擎](https://www.volcengine.com/)的API实现

## Deepseek 70B模型部署

### 运行环境

Deepseek 70B 的模型部署于校内服务器

显卡型号：`NVIDIA NVIDIA RTX™ 6000 Ada Generation`

用于服务的显卡数量：2

每张显卡的显存：49140 MiB

### 模型选择

我们选择 Deepseek-70B 的 `AWQ` 量化后版本，模型网站请[点击此处](https://huggingface.co/Valdemardi/DeepSeek-R1-Distill-Llama-70B-AWQ)

### 模型部署

我们使用 `vLLM` 进行模型部署，具体的部署指令如下：

```
vllm serve Valdemardi/DeepSeek-R1-Distill-Llama-70B-AWQ  --dtype float16   --port 11435   --host 0.0.0.0   --trust-remote-code   --tensor-parallel-size 2   --max-model-len 4096   --quantization awq   --load-format auto
```

### 并发测试

我们对部署的`Deepseek-70B`模型进行了并发测试，实验过程：

使用测试脚本同时发送若干条请求，其提示词为：

```
请为我介绍 Dynamic Programming，并提供一段 Java 代码示例
```

下表为测试结果：

|测试并发指令数量|第1次实验 最快结果\最慢结果\平均结果\峰值token输出速度|第2次实验 最快结果\最慢结果\平均结果\峰值token输出速度|第3次实验 最快结果\最慢结果\平均结果\峰值token输出速度|
|-|-|-|-|
|1|48.99s\48.99s\48.99s\35.5 tokens/s|116.87s\116.87s\116.87s\35.4 tokens/s|47.27s\47.27s\47.27s\35.2 tokens/s|
|5|33.32s\68.69s\50.11s\162.5 tokens/s|34.53s\88.94s\53.42s\162.5 tokens/s|34.08s\87.49s\50.89s\162.4 tokens/s|
|10|46.43s\76.93s\60.27s\296.1 tokens/s|45.58s\106.85s\63.07s\295.5 tokens/s|39.20s\103.33s\67.38s\295.5 tokens/s|
|20|62.30s\140.69s\96.90s\365.2 tokens/s|47.83s\123.40s\89.57s\374.4 tokens/s|63.58s\142.11s\90.18s\375.1 tokens/s|
|50|99.61s\223.68s\151.60s\482.3 tokens/s|100.87s\249.57s\168.38s\486.1 tokens/s|93.24s\212.01s\161.86s\488.4 tokens/s|
|100|139.37s\397.85s\281.58s\562.7 tokens/s|174.60s\397.25s\287.83s\575.1 tokens/s|200.08s\380.70s\283.63s\577.3 tokens/s|
|200|305.08s\807.73s\581.89s\624.6 tokens/s|285.40s\839.56s\591.74s\641.2 tokens/s|310.48s\752.43s\555.31s\640.5 tokens/s|
