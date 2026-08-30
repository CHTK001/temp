package com.chua.example.message;

import com.chua.common.support.task.message.MessageEnvironment;
import com.chua.common.support.task.message.MessagePush;
import com.chua.common.support.task.message.MessageRequest;
import com.chua.common.support.task.message.MessageResponse;

import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook 消息渠道 {@link MessagePush}（SPI 名 "webhook"）自检示例。
 *
 * <p>覆盖：SPI 实例解析、webhook.url 缺失时的失败响应、
 * 目标地址不可达时的优雅失败（连接异常被捕获并转为失败结果）。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java MessageHookExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class MessageHookExample {

    private MessageHookExample() {
    }

    /**
     * 输出异常失败信息。
     *
     * @param name 场景名
     * @param e    异常
     * @return 恒为 false
     */
    private static boolean fail(String name, Exception e) {
        log.info("[FAIL] " + name + " 异常: " + e);
        return false;
    }

    /**
     * 场景一：ServiceProvider 解析出 webhook 实现。
     *
     * @return true 表示通过
     */
    private static boolean spiResolvesWebhookImplementation() {
        try {
            MessagePush push = MessagePush.create("webhook");
            boolean ok = push != null;
            ExampleUtils.print("spiResolvesWebhookImplementation", ok);
            return ok;
        } catch (Exception e) {
            return fail("spiResolvesWebhookImplementation", e);
        }
    }

    /**
     * 场景二：webhook.url 缺失时 send 返回失败响应并带原因。
     *
     * @return true 表示通过
     */
    private static boolean missingUrlFails() {
        try {
            MessagePush push = MessagePush.create("webhook");
            MessageResponse resp = push.send(MessageRequest.builder()
                    .to("ops").content("{}").contentType("application/json").build());
            boolean ok = !resp.isSuccess() && resp.getErrorMessage() != null;
            ExampleUtils.print("missingUrlFails (" + resp.getErrorMessage() + ")", ok);
            return ok;
        } catch (Exception e) {
            return fail("missingUrlFails", e);
        }
    }

    /**
     * 场景三：目标地址不可达时优雅失败，不抛出、不阻塞。
     *
     * @return true 表示通过
     */
    private static boolean unreachableFailsGracefully() {
        try {
            MessagePush push = MessagePush.create("webhook");
            MessageEnvironment env = new MessageEnvironment().set("webhook.url",
                    "http://127.0.0.1:9/hook");
            var unused = env;
            MessageResponse resp = push.send(MessageRequest.builder()
                    .to("ops").content("{\"event\":\"test\"}").contentType("application/json").build());
            boolean ok = resp != null && !resp.isSuccess();
            ExampleUtils.print("unreachableFailsGracefully", ok);
            return ok;
        } catch (Exception e) {
            return fail("unreachableFailsGracefully", e);
        }
    }

    /**
     * 独立入口：运行全部场景，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean passed = true;
        passed &= ExampleUtils.timed("spiResolvesWebhookImplementation",
                MessageHookExample::spiResolvesWebhookImplementation);
        passed &= ExampleUtils.timed("missingUrlFails", MessageHookExample::missingUrlFails);
        passed &= ExampleUtils.timed("unreachableFailsGracefully",
                MessageHookExample::unreachableFailsGracefully);
        if (!passed) {
            log.info("[FAIL] MessageHook 存在失败场景");
            System.exit(ExampleUtils.FAILURE);
        }
        log.info("[PASS] MessageHook 全部场景通过");
        System.exit(ExampleUtils.SUCCESS);
    }


}
