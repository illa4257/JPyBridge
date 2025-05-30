import threading, typing, sys
import traceback


def test():
    sys.stderr.write("GGGGG\n")
    sys.stderr.flush()

def __get_by_key(dictionary, v):
    return next((k for k, v in dictionary.items() if v == v), None)

def REFLECTION_CALL(func, args: list):
    e = 'func('
    if len(args) > 0:
        e += 'args[0]'
        for i in range(1, len(args)):
            e += f', args[{i}]'
    return eval(e + ')')

class JavaObject:
    def __init__(self, bridge: '__Bridge', java_id: int):
        self.__bridge = bridge
        self.__id = java_id

    def __getattr__(self, item):
        pass

class __Bridge:
    def __init__(self, input_stream: typing.BinaryIO, output_stream: typing.BinaryIO):
        self.inputStream = input_stream
        self.outputStream = output_stream

        self.out_locker = threading.Lock()

        self.__objects = {}
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

                d1 = None
                throw = False
                result = None

                if c == 4: # CALL
                    obj = b.readObject()
                    name = b.inputStream.read(int.from_bytes(b.inputStream.read(4), 'big')).decode("utf-8")
                    args = []
                    for n in range(int.from_bytes(b.inputStream.read(4), 'big')):
                        args.append(b.readObject())
                    d1 = [ obj, name, args ]
                elif c == 5: # EXEC
                    d1 = b.inputStream.read(int.from_bytes(b.inputStream.read(4), 'big')).decode("utf-8")
                else:
                    sys.stderr.write(f'Unknown code: {c}\n')
                    sys.stderr.flush()

                with self.cond:
                    self.cond.notify_all()

                if c == 4:
                    try:
                        if d1[0] is None:
                            result = REFLECTION_CALL(eval(d1[1]), d1[2])
                        else:
                            result = REFLECTION_CALL(eval(f'd1[0].{d1[1]}'), d1[2])
                    except:
                        throw = True
                        result = sys.exc_info()[0]
                        sys.stderr.write(traceback.format_exc() + "\n")
                        sys.stderr.flush()
                elif c == 5:
                    try:
                        e = {}
                        exec(d1, e)
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

    def readObject(self):
        c = int.from_bytes(self.inputStream.read(1), 'big')
        if c == 0:
            return None
        if c == 9:
            return self.inputStream.read(int.from_bytes(self.inputStream.read(4), 'big')).decode('utf-8')
        if c == 11:
            return self.__objects[int.from_bytes(self.inputStream.read(8), 'big')]
        sys.stderr.write(f'[JPyBridge] Unknown type code: {c}\n')
        sys.stderr.flush()
        return None

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
            self.outputStream.write(b'\x09')
            o = o.encode("utf-8")
            self.outputStream.write(len(o).to_bytes(4, 'big'))
            self.outputStream.write(o)
            return
        self.__objects[id(o)] = o
        self.outputStream.write(b'\x0b')
        self.outputStream.write(id(o).to_bytes(8, 'big'))
        sys.stderr.write(f'[JPyBridge] Unknown type: {o}\n')
        sys.stderr.flush()

__is = None
__out = None

if __is is None:
    __is = sys.stdin.buffer
    __out = sys.stdout.buffer

__Bridge(__is, __out)