import threading, typing, sys


def __get_by_key(dictionary, v):
    return next((k for k, v in dictionary.items() if v == v), None)

class __Bridge:
    def __init__(self, input_stream: typing.BinaryIO, output_stream: typing.BinaryIO):
        self.inputStream = input_stream
        self.outputStream = output_stream

        self.out_locker = threading.Lock()

        self.__lockers = {}
        self.threads = {}

        b = self

        class __BridgeThread(threading.Thread):
            def __init__(self, thread_id: int):
                threading.Thread.__init__(self)
                self.thread_id = thread_id
                self.cond = threading.Condition()
                self.counter = 1

            def run(self):
                c = int.from_bytes(b.inputStream.read(1), 'big')

                code = None

                if c == 5: # EXEC
                    code = b.inputStream.read(int.from_bytes(b.inputStream.read(4), 'big')).decode("utf-8")
                else:
                    sys.stderr.write(f'Unknown code: {c}\n')
                    sys.stderr.flush()

                with self.cond:
                    self.cond.notify_all()

                throw = False
                result = None
                try:
                    e = {}
                    exec(code, e)
                    if 'result' in e:
                        result = e['result']
                except:
                    throw = True
                    result = sys.exc_info()[0]

                if self.counter == 1:
                    b.threads.pop(self.thread_id)
                else:
                    self.counter -= 1

                with b.out_locker:
                    b.outputStream.write(self.thread_id.to_bytes(8, 'big'))
                    if throw:
                        b.outputStream.write(b'\x01')
                    else:
                        b.outputStream.write(b'\x00')
                    b.writeObject(result)
                    b.outputStream.flush()

        while True:
            id = int.from_bytes(self.inputStream.read(8), 'big')
            if id not in self.threads:
                self.threads[id] = th = __BridgeThread(id)
                with th.cond:
                    th.start()
                    th.cond.wait()
            else:
                sys.stderr.write("This thread still exists!\n")
                sys.stderr.flush()

    def writeObject(self, o):
        if o is None:
            self.outputStream.write(b'\x00')
            return
        if type(o) == bool:
            if o:
                self.outputStream.write(b'\x01\x01')
            else:
                self.outputStream.write(b'\x01\x00')
            return
        if type(o) == int:
            self.outputStream.write(b'\x05')
            self.outputStream.write(o.to_bytes(8, 'big'))
            return
        if type(o) == str:
            self.outputStream.write(b'\x08')
            o = o.encode("utf-8")
            self.outputStream.write(len(o).to_bytes(4, 'big'))
            self.outputStream.write(o)
            return
        self.outputStream.write(b'\x00')
        sys.stderr.write(f'[JPyBridge] Unknown type: {o}\n')
        sys.stderr.flush()

__is = None
__out = None

if __is is None:
    __is = sys.stdin.buffer
    __out = sys.stdout.buffer

__Bridge(__is, __out)